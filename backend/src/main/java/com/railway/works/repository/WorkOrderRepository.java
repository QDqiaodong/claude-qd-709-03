package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.WorkOrder;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    List<WorkOrder> findAllByOrderByWindowDateAscStartTimeAsc();

    List<WorkOrder> findAllBySectionIdOrderByWindowDateAsc(Long sectionId);

    boolean existsByOrderNo(String orderNo);
}
