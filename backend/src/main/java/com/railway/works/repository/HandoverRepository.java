package com.railway.works.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.railway.works.domain.Handover;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    /** 列表按最近提交时间倒序 */
    List<Handover> findAllByOrderBySubmittedAtDesc();

    List<Handover> findAllBySectionIdOrderBySubmittedAtDesc(Long sectionId);

    List<Handover> findAllByStatusOrderBySubmittedAtDesc(String status);

    List<Handover> findAllBySectionIdAndStatusOrderBySubmittedAtDesc(Long sectionId, String status);

    boolean existsByHandoverNo(String handoverNo);

    long countByWorkOrderIdAndStatus(Long workOrderId, String status);

    /** 某张作业单最近一张"进行中"（待复核/已退回）的申请，作业页展示交接入口用 */
    Optional<Handover> findFirstByWorkOrderIdAndStatusInOrderBySubmittedAtDesc(
            Long workOrderId, Collection<String> statuses);

    /** 失效检测：全部仍在复核窗口内的申请（待复核 + 已退回） */
    @Query("select h from Handover h where h.status in ('PENDING','REJECTED')")
    List<Handover> findAllActiveForCheck();

    /** 重提链路：某张旧申请被哪些新申请引用 */
    List<Handover> findAllByReapplyOfIdOrderBySubmittedAtAsc(Long reapplyOfId);
}
