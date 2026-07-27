package io.github.shardkiht.rentdetective.app.controller;

import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.app.service.ListingService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    /**
     * GET /api/listings — 返回全部 Listing 列表。
     */
    @GetMapping
    public List<Listing> listAll() {
        return listingService.list();
    }

    /**
     * POST /api/listings/import — 批量导入房源。
     */
    @PostMapping("/import")
    public ListingService.ImportResult importListings(@RequestBody List<Listing> listings) {
        return listingService.importListings(listings);
    }

    /**
     * DELETE /api/listings — 清空全部房源数据。
     */
    @DeleteMapping
    public String clearAll() {
        int count = listingService.clearAll();
        return "已清空 " + count + " 条房源数据";
    }
}
