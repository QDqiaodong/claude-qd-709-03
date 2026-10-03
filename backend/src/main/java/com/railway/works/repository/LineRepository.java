package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Line;

public interface LineRepository extends JpaRepository<Line, Long> {

    List<Line> findAllByOrderByIdAsc();

    boolean existsByName(String name);
}
