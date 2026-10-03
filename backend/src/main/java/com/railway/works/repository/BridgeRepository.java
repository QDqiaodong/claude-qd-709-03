package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Bridge;

/** Bridge 子类仓库：TABLE_PER_CLASS 下对应 Bridge 单表。 */
public interface BridgeRepository extends JpaRepository<Bridge, Long> {

    List<Bridge> findAllBySectionIdOrderByMileageAsc(Long sectionId);

    List<Bridge> findAllByOrderByMileageAsc();
}
