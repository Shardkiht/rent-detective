package io.github.shardkiht.rentdetective.app.service;

import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.app.mapper.ListingMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListingService {

    private final ListingMapper listingMapper;

    public ListingService(ListingMapper listingMapper) {
        this.listingMapper = listingMapper;
    }

    public void importListing(Listing listing) {
        listingMapper.insert(listing);
    }

    public List<Listing> list() {
        return listingMapper.selectList(null);
    }

    public Listing findById(Long id) {
        return listingMapper.selectById(id);
    }
}
