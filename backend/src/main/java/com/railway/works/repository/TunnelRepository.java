package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Tunnel;

/** Tunnel 子类仓库：TABLE_PER_CLASS 下对应 Tunnel 单表。 */
public interface TunnelRepository extends JpaRepository<Tunnel, Long> {

    List<Tunnel> findAllBySectionIdOrderByMileageAsc(Long sectionId);

    List<Tunnel> findAllByOrderByMileageAsc();
}
