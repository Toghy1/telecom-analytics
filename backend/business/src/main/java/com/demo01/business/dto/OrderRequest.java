package com.demo01.business.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 办理套餐入参：{@code {package_id}}。 */
@Data
public class OrderRequest {

    @NotNull(message = "请选择套餐")
    private Long packageId;
}
