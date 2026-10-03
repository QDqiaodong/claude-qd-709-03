package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.RecheckBatch;

public interface RecheckBatchRepository extends JpaRepository<RecheckBatch, Long> {

    List<RecheckBatch> findAllByOrderByCreatedAtDesc();

    List<RecheckBatch> findAllBySectionIdOrderByCreatedAtDesc(Long sectionId);

    List<RecheckBatch> findAllByStatusOrderByCreatedAtDesc(String status);

    List<RecheckBatch> findAllBySectionIdAndStatusOrderByCreatedAtDesc(Long sectionId, String status);

    boolean existsByBatchNo(String batchNo);
}
