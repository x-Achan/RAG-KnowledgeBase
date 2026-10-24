package com.ccy.ragkb.repository;

import com.ccy.ragkb.entity.IndexTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndexTaskRepository extends JpaRepository<IndexTask, Long> {

    // 根据文档id查询IndexTask
    Optional<IndexTask> findTopByDocIdOrderByCreatedAtDesc(Long docId);
}