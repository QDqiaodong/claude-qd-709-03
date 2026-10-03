package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Switch;

/** Switch 子类仓库：TABLE_PER_CLASS 下对应 Switch 单表。 */
public interface SwitchRepository extends JpaRepository<Switch, Long> {

    List<Switch> findAllBySectionIdOrderByMileageAsc(Long sectionId);

    List<Switch> findAllByOrderByMileageAsc();
}
