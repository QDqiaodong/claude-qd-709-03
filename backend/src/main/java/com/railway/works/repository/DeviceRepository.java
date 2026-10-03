package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.railway.works.domain.Device;

/**
 * 设备台账：直接挂在抽象基类 Device 上。
 *
 * <p>TABLE_PER_CLASS 下所有查询都是多态查询——查基类会把 rail / switch / sleeper / bridge / tunnel
 * 五张表 UNION 起来，返回各自的具体子类实例（页面靠 instanceof 落到对应 Tab）。
 */
public interface DeviceRepository extends JpaRepository<Device, Long> {

    /** 多态查询：按区段取全部类型设备 */
    List<Device> findAllBySectionIdOrderByMileageAsc(Long sectionId);

    /** 多态 JPQL：按状态跨全部子类表检索 */
    @Query("select d from Device d where d.status = :status order by d.mileage asc")
    List<Device> findPolymorphicByStatus(@Param("status") String status);

    boolean existsByDeviceNo(String deviceNo);
}
