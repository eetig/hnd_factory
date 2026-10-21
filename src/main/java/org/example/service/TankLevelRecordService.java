package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.TankLevelQueryDTO;
import org.example.dto.TankLevelSaveDTO;
import org.example.dto.TankLevelVO;

import java.util.List;

/**
 * 月底储罐液位记录：查询 + 录入维护。
 *
 * <p>变更-004 落地时这张表是纯只读的（数据靠 SQL 补录）；本变更加上人工维护入口 ——
 * 改数、补录漏抄的行、删掉录错的行。写操作全部要求 {@code tank_level:edit} /
 * {@code tank_level:delete} 权限（见 TankLevelController），查询仍然免登录。
 *
 * <p>业务失败（校验不过、记录不存在、撞唯一键）一律抛 {@link org.example.exception.BusinessException}，
 * 由 GlobalExceptionHandler 转成 {@code Result.error}，不要在这里自己拼错误返回体 ——
 * 那样每个方法都得返回一个 Result 包装，调用方还要拆一层。
 */
public interface TankLevelRecordService {

    /**
     * 按条件分页查询液位记录。
     *
     * <p>改造前这个方法返回**全部命中行**、分页由前端 {@code slice} 完成。注意它和
     * 领料/入库那两条不一样 —— 那两条是「全量拉回来再本地过滤」，这条的**条件过滤早就在
     * 服务端**了，所以只是「补上分页」，筛选逻辑没动。
     */
    IPage<TankLevelVO> listPage(TankLevelQueryDTO query);

    /** 属地下拉选项：库里实际出现过的值，按中文习惯排序（不用写死字典） */
    List<String> listLocations();

    /**
     * 容器名称候选：库里实际录过的名称（去重、按中文习惯排序）。
     *
     * <p>为什么要有它（2026-10-20，变更-129）：容器名称的候选原先只来自**设备台账**，
     * 而现场经常先手打一个台账里还没有的名字（新装罐、临时编号）。手打过的名字如果不进候选，
     * 下次录入就得再打一遍，还容易打出两种写法 —— 同一天同一容器的重复提示也就拦不住了。
     */
    List<String> listTankNames();

    /**
     * 某个容器**最近一次**记录的属地（没有记录时返回 null）。
     *
     * <p>用途（2026-10-21 使用方要求「填容器名称后自动填充属地」）：录一条新记录时，
     * 同一个容器的"属地"几乎不会变，查上一次就能直接带出来，省一次选择。
     *
     * <p>为什么查**这个容器**而不是台账：设备台账里的「车间/装置」是"三甲车间/罐区"这一级，
     * 而液位记录的属地是"1#4L"这种车间+楼层编号 —— 两个口径对不上，台账推导不出属地。
     *
     * <p>为什么取**最近**一条：同一个容器历史上改过属地（搬家/改编号）时，以最新为准。
     */
    String findLastLocationByTankName(String tankName);

    /**
     * 新增或编辑一条记录（{@code dto.id} 为空即新增）。
     *
     * @return 保存后的完整记录（含图据数组），供前端就地更新那一行
     */
    TankLevelVO saveRecord(TankLevelSaveDTO dto);

    /**
     * 删除一条记录，连同它名下的全部图据（数据库行 + file-service 上的文件）。
     *
     * <p>子表没有外键，这一步必须显式做，否则留下孤儿行与 MinIO 孤儿文件。
     */
    void deleteRecord(Long id);
}
