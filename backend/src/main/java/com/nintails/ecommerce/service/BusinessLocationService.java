package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.BusinessLocationDto;
import com.nintails.ecommerce.dto.BusinessLocationRequest;
import com.nintails.ecommerce.entity.BusinessLocation;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.BusinessLocationRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessLocationService {

    private final BusinessLocationRepository businessLocationRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<BusinessLocationDto> list(String email) {
        User owner = getOwner(email);
        return businessLocationRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessLocationDto> listForAdministrator(Long ownerId) {
        User owner = userRepository.findByIdAndRole(ownerId, User.Role.OWNER)
                .orElseThrow(() -> new ResourceNotFoundException("Owner", ownerId));
        return businessLocationRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public BusinessLocationDto create(String email, BusinessLocationRequest request) {
        User owner = getOwner(email);
        if (!Boolean.TRUE.equals(request.getVerifiedByOwner())) {
            throw new BadRequestException("Confirm that the pickup address is exact before saving it.");
        }
        BusinessLocation location = BusinessLocation.builder()
                .owner(owner)
                .name(request.getName().trim())
                .street(request.getStreet().trim())
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .postalCode(request.getPostalCode())
                .contactPhone(request.getContactPhone())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .verifiedByOwner(Boolean.TRUE.equals(request.getVerifiedByOwner()))
                .build();
        return toDto(businessLocationRepository.save(location));
    }

    @Transactional
    public void delete(String email, Long id) {
        User owner = getOwner(email);
        BusinessLocation location = businessLocationRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Business location", id));
        if (productRepository.existsByBusinessLocation_Id(id)) {
            throw new ConflictException(
                    "Move or remove products using this pickup location before deleting it.");
        }
        businessLocationRepository.delete(location);
    }

    private User getOwner(String email) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (owner.getRole() != User.Role.OWNER) {
            throw new AccessDeniedException("An owner account is required.");
        }
        return owner;
    }

    private BusinessLocationDto toDto(BusinessLocation location) {
        return BusinessLocationDto.builder()
                .id(location.getId())
                .name(location.getName())
                .street(location.getStreet())
                .city(location.getCity())
                .state(location.getState())
                .postalCode(location.getPostalCode())
                .country(location.getCountry())
                .contactPhone(location.getContactPhone())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .verifiedByOwner(Boolean.TRUE.equals(location.getVerifiedByOwner()))
                .build();
    }
}
