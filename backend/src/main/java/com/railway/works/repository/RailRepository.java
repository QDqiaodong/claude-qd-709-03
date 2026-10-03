package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Rail;

/** Rail 子类仓库：TABLE_PER_CLASS 下对应 Rail 单表。 */
public interface RailRepository extends JpaRepository<Rail, Long> {

    List<Rail> findAllBySectionIdOrderByMileageAsc(Long sectionId);

    List<Rail> findAllByOrderByMileageAsc();
}
