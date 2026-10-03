package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Sleeper;

/** Sleeper 子类仓库：TABLE_PER_CLASS 下对应 Sleeper 单表。 */
public interface SleeperRepository extends JpaRepository<Sleeper, Long> {

    List<Sleeper> findAllBySectionIdOrderByMileageAsc(Long sectionId);

    List<Sleeper> findAllByOrderByMileageAsc();
}
