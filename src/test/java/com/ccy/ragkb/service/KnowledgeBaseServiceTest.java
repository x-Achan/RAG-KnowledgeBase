package com.ccy.ragkb.service;

import com.ccy.ragkb.dto.KnowledgeBaseCreateRequest;
import com.ccy.ragkb.dto.KnowledgeBaseVO;
import com.ccy.ragkb.entity.KbDocument;
import com.ccy.ragkb.entity.KbPermission;
import com.ccy.ragkb.entity.KnowledgeBase;
import com.ccy.ragkb.repository.DocChunkRepository;
import com.ccy.ragkb.repository.IndexTaskRepository;
import com.ccy.ragkb.repository.KbDocumentRepository;
import com.ccy.ragkb.repository.KbPermissionRepository;
import com.ccy.ragkb.repository.KnowledgeBaseRepository;
import com.ccy.ragkb.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseRepository kbRepository;
    @Mock
    private KbPermissionRepository permissionRepository;
    @Mock
    private KbDocumentRepository documentRepository;
    @Mock
    private DocChunkRepository chunkRepository;
    @Mock
    private IndexTaskRepository taskRepository;
    @Mock
    private MinioStorageService minioService;
    @Mock
    private IndexService indexService;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUpUserContext() {
        UserContext.set(42L, "dept-rd", "USER");
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createShouldSaveKnowledgeBaseAndGrantCreatorAdminPermission() {
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setName("研发知识库");
        request.setDescription("研发规范和技术文档");
        request.setDepartmentId("dept-rd");
        request.setIsPublic(false);

        when(kbRepository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase kb = invocation.getArgument(0);
            kb.setId(100L);
            return kb;
        });

        KnowledgeBase result = knowledgeBaseService.create(request);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("研发知识库");
        assertThat(result.getCreatedBy()).isEqualTo(42L);

        ArgumentCaptor<KbPermission> permissionCaptor = ArgumentCaptor.forClass(KbPermission.class);
        verify(permissionRepository).save(permissionCaptor.capture());
        KbPermission permission = permissionCaptor.getValue();
        assertThat(permission.getKbId()).isEqualTo(100L);
        assertThat(permission.getSubjectType()).isEqualTo("USER");
        assertThat(permission.getSubjectId()).isEqualTo("42");
        assertThat(permission.getPermission()).isEqualTo("ADMIN");
        assertThat(permission.getGrantedBy()).isEqualTo(42L);
    }

    @Test
    void listAccessibleShouldReturnEveryKnowledgeBaseAsAdminForAdminUser() {
        UserContext.set(1L, "admin-dept", "ADMIN");
        KnowledgeBase first = knowledgeBase(1L, "公共规范", true, false);
        KnowledgeBase second = knowledgeBase(2L, "内部资料", false, false);
        when(kbRepository.findByIsDeletedFalse()).thenReturn(List.of(first, second));

        List<KnowledgeBaseVO> result = knowledgeBaseService.listAccessible();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(KnowledgeBaseVO::getPermission)
                .containsOnly("ADMIN");
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void listAccessibleShouldMergeUserDepartmentAndPublicPermissionsUsingHighestLevel() {
        KnowledgeBase departmentKb = knowledgeBase(1L, "部门知识库", false, false);
        KnowledgeBase userKb = knowledgeBase(2L, "个人知识库", false, false);
        KnowledgeBase publicKb = knowledgeBase(3L, "公共知识库", true, false);
        KnowledgeBase deletedKb = knowledgeBase(4L, "已删除知识库", false, true);

        when(permissionRepository.findBySubjectTypeAndSubjectId("DEPARTMENT", "dept-rd"))
                .thenReturn(List.of(permission(1L, "READ"), permission(2L, "READ")));
        when(permissionRepository.findBySubjectTypeAndSubjectId("USER", "42"))
                .thenReturn(List.of(permission(1L, "WRITE"), permission(4L, "ADMIN")));
        when(kbRepository.findByIsPublicTrueAndIsDeletedFalse()).thenReturn(List.of(publicKb));
        when(kbRepository.findAllById(argThat(ids -> ids != null)))
                .thenReturn(List.of(departmentKb, userKb, publicKb, deletedKb));

        List<KnowledgeBaseVO> result = knowledgeBaseService.listAccessible();

        assertThat(result).hasSize(3);
        assertThat(permissionOf(result, 1L)).isEqualTo("WRITE");
        assertThat(permissionOf(result, 2L)).isEqualTo("READ");
        assertThat(permissionOf(result, 3L)).isEqualTo("READ");
        assertThat(result).extracting(KnowledgeBaseVO::getId).doesNotContain(4L);
    }

    @Test
    void uploadDocumentShouldStoreFileSaveMetadataAndSubmitIndexTask() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.PDF", "application/pdf", "pdf-content".getBytes());
        when(minioService.upload(10L, file)).thenReturn("kb/10/abc-guide.PDF");
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            document.setId(200L);
            return document;
        });

        KbDocument result = knowledgeBaseService.uploadDocument(10L, file);

        assertThat(result.getId()).isEqualTo(200L);
        assertThat(result.getKbId()).isEqualTo(10L);
        assertThat(result.getFileName()).isEqualTo("guide.PDF");
        assertThat(result.getFileType()).isEqualTo("PDF");
        assertThat(result.getFileSize()).isEqualTo(file.getSize());
        assertThat(result.getMinioPath()).isEqualTo("kb/10/abc-guide.PDF");
        assertThat(result.getUploadedBy()).isEqualTo(42L);
        verify(indexService).submitIndexTask(200L);
    }

    @Test
    void uploadDocumentShouldRejectUnsupportedFileBeforeCallingMinio() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> knowledgeBaseService.uploadDocument(10L, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不支持的文件类型");

        verifyNoInteractions(minioService, documentRepository, indexService);
    }

    @Test
    void deleteDocumentShouldSoftDeleteDocumentAndRemoveChunksAndObject() {
        KbDocument document = document(300L);
        document.setMinioPath("kb/10/abc-guide.pdf");
        when(documentRepository.findById(300L)).thenReturn(Optional.of(document));

        knowledgeBaseService.deleteDocument(300L);

        assertThat(document.getIsDeleted()).isTrue();
        verify(documentRepository).save(document);
        verify(chunkRepository).deleteByDocId(300L);
        verify(minioService).delete("kb/10/abc-guide.pdf");
    }

    @Test
    void deleteDocumentShouldFailWhenDocumentDoesNotExist() {
        when(documentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeBaseService.deleteDocument(404L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文档不存在：404");

        verify(documentRepository, never()).save(any());
        verifyNoInteractions(chunkRepository, minioService);
    }

    @Test
    void reindexShouldIncrementVersionResetStatusAndSubmitTask() {
        KbDocument document = document(500L);
        document.setVersion(3);
        document.setStatus(KbDocument.DocumentStatus.FAILED);
        document.setErrorMsg("上一次索引失败");
        when(documentRepository.findById(500L)).thenReturn(Optional.of(document));

        knowledgeBaseService.reindex(500L);

        assertThat(document.getVersion()).isEqualTo(4);
        assertThat(document.getStatus()).isEqualTo(KbDocument.DocumentStatus.PENDING);
        assertThat(document.getErrorMsg()).isNull();
        verify(documentRepository).save(document);
        verify(indexService).submitIndexTask(500L);
    }

    private static KnowledgeBase knowledgeBase(Long id, String name, boolean isPublic, boolean isDeleted) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName(name);
        kb.setDescription(name + "描述");
        kb.setDepartmentId("dept-rd");
        kb.setIsPublic(isPublic);
        kb.setIsDeleted(isDeleted);
        kb.setCreatedBy(42L);
        kb.setCreatedAt(LocalDateTime.of(2026, 10, 23, 10, 0));
        return kb;
    }

    private static KbPermission permission(Long kbId, String level) {
        KbPermission permission = new KbPermission();
        permission.setKbId(kbId);
        permission.setPermission(level);
        return permission;
    }

    private static KbDocument document(Long id) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setKbId(10L);
        document.setFileName("guide.pdf");
        document.setVersion(1);
        return document;
    }

    private static String permissionOf(List<KnowledgeBaseVO> knowledgeBases, Long kbId) {
        return knowledgeBases.stream()
                .filter(kb -> kbId.equals(kb.getId()))
                .findFirst()
                .orElseThrow()
                .getPermission();
    }
}
