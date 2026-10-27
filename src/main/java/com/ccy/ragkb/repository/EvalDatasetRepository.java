package com.ccy.ragkb.repository;

import com.ccy.ragkb.entity.EvalDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalDatasetRepository extends JpaRepository<EvalDataset, Long> {

    List<EvalDataset> findByKbId(Long kbId);
}