package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.railway.works.domain.RecheckItem;

public interface RecheckItemRepository extends JpaRepository<RecheckItem, Long> {

    List<RecheckItem> findAllByBatchIdOrderByDeviceTypeAscMeasuredMileageAscIdAsc(Long batchId);

    /** 某台设备在哪些未完成（草稿/待复核/已退回）批次里有明细——"重复占用"判定 */
    @Query("select i from RecheckItem i where i.deviceId = :deviceId and i.batchStatus in ('DRAFT','PENDING','REJECTED')")
    List<RecheckItem> findActiveByDeviceId(@Param("deviceId") Long deviceId);

    /** 一批设备各自的未完成占用明细（到期汇总列表标记"已被占用"用） */
    @Query("select i from RecheckItem i where i.deviceId in :deviceIds and i.batchStatus in ('DRAFT','PENDING','REJECTED')")
    List<RecheckItem> findActiveByDeviceIdIn(@Param("deviceIds") List<Long> deviceIds);
}
