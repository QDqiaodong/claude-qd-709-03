package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Inspection;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findAllByOrderByInspectDateDesc();

    List<Inspection> findAllBySectionIdOrderByInspectDateDesc(Long sectionId);

    boolean existsByInspectNo(String inspectNo);
}
