package com.redface.query;

import com.redface.dto.MyPhotoItem;
import com.redface.dto.MyPhotosResponse;
import com.redface.mapper.C9QueryMapper;
import com.redface.service.UserMembershipService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * API-4 我的写真只读查询服务。
 */
@Service
public class PhotoQueryService {
    private final C9QueryMapper c9QueryMapper;
    private final UserMembershipService userMembershipService;

    public PhotoQueryService(C9QueryMapper c9QueryMapper, UserMembershipService userMembershipService) {
        this.c9QueryMapper = c9QueryMapper;
        this.userMembershipService = userMembershipService;
    }

    public MyPhotosResponse getMyPhotos(String userId) {
        List<MyPhotoItem> items = c9QueryMapper.findPhotosByUser(userId);
        MyPhotosResponse response = new MyPhotosResponse();
        response.setItems(items);
        response.setTotal(items.size());
        response.setMembership(userMembershipService.getSummary(userId));
        return response;
    }
}
