package com.example.restaurant_saas.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeProductImage(MultipartFile file);
    String storeRestaurantLogo(MultipartFile file);
}
