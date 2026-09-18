package org.example.constant;

/**
 * 权限标识常量，供 @SaCheckPermission 注解与角色权限中间表统一使用。
 */
public interface PermConstants {
    String WORK_ORDER_VIEW = "work_order:view";               // 查看工单
    String WORK_ORDER_ADD = "work_order:add";                 // 新增工单
    String WORK_ORDER_EDIT = "work_order:edit";               // 编辑工单
    String WORK_ORDER_DELETE = "work_order:delete";           // 删除工单
    String WORK_ORDER_IMPORT = "work_order:import";           // 导入工单
    String WORK_ORDER_IMAGE_UPLOAD = "work_order:image:upload"; // 工单图片上传
    String WORK_ORDER_IMAGE_DELETE = "work_order:image:delete"; // 删除工单图片
}
