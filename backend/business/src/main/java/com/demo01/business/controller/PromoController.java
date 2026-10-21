package com.demo01.business.controller;

import com.demo01.business.common.Result;
import com.demo01.business.service.PromoService;
import com.demo01.business.vo.PromoVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 促销活动查询（只返回「进行中」的，见 {@code PromoService}）。 */
@RestController
@RequestMapping("/api/promos")
public class PromoController {

    private final PromoService promoService;

    public PromoController(PromoService promoService) {
        this.promoService = promoService;
    }

    @GetMapping
    public Result<List<PromoVO>> list(@RequestParam(required = false) Integer limit) {
        return Result.ok(promoService.list(limit));
    }
}
