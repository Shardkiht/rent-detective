package io.github.shardkiht.rentdetective.app.service;

import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.app.mapper.ListingMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ListingService {

    private final ListingMapper listingMapper;

    public ListingService(ListingMapper listingMapper) {
        this.listingMapper = listingMapper;
    }

    public List<Listing> list() {
        return listingMapper.selectList(null);
    }

    public Listing findById(Long id) {
        return listingMapper.selectById(id);
    }

    /**
     * 批量导入房源，支持幂等（按 id 跳过已存在）。
     *
     * @return 导入结果 {imported, skipped, errors}
     */
    public ImportResult importListings(List<Listing> listings) {
        int imported = 0;
        int skipped = 0;
        List<ErrorDetail> errors = new ArrayList<>();

        for (Listing item : listings) {
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                errors.add(new ErrorDetail(item.getId(), "title 为空，跳过"));
                continue;
            }

            if (listingMapper.existsById(item.getId())) {
                skipped++;
                continue;
            }

            item.setEvalGroup(computeEvalGroup(item.getRiskTags()));

            try {
                listingMapper.insertCustom(item);
                imported++;
            } catch (Exception e) {
                errors.add(new ErrorDetail(item.getId(), e.getMessage()));
            }
        }

        return new ImportResult(imported, skipped, errors);
    }

    private static String computeEvalGroup(String riskTags) {
        if (riskTags == null) {
            return "normal";
        }
        String lower = riskTags.toLowerCase();
        if (lower.contains("not_listing")) {
            return "not_listing";
        }
        if (lower.contains("info_insufficient")) {
            return "info_insufficient";
        }
        return "normal";
    }

    public record ImportResult(int imported, int skipped, List<ErrorDetail> errors) {
    }

    public record ErrorDetail(Long id, String message) {
    }
}
