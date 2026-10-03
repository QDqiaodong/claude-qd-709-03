package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Material;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findAllByOrderByIdAsc();
}
