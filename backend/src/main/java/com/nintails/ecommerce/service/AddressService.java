package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AddressDto;
import com.nintails.ecommerce.entity.Address;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.repository.AddressRepository;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public List<AddressDto> getUserAddresses(String email) {
        User user = getUser(email);
        return addressRepository.findByUserId(user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public AddressDto addAddress(String email, AddressDto dto) {
        User user = getUser(email);
        validateCoordinates(dto);

        // If this is first address or set as default, clear others
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            addressRepository.findByUserIdAndIsDefaultTrue(user.getId())
                    .ifPresent(a -> { a.setIsDefault(false); addressRepository.save(a); });
        }

        boolean isFirst = addressRepository.findByUserId(user.getId()).isEmpty();

        Address address = Address.builder()
                .user(user)
                .fullName(dto.getFullName())
                .street(dto.getStreet())
                .city(dto.getCity())
                .postalCode(dto.getPostalCode())
                .country(dto.getCountry())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .isDefault(isFirst || Boolean.TRUE.equals(dto.getIsDefault()))
                .build();

        return toDto(addressRepository.save(address));
    }

    @Transactional
    public AddressDto updateAddress(String email, Long addressId, AddressDto dto) {
        User user = getUser(email);
        validateCoordinates(dto);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));

        if (Boolean.TRUE.equals(dto.getIsDefault()) && !address.getIsDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(user.getId())
                    .ifPresent(a -> { a.setIsDefault(false); addressRepository.save(a); });
        }

        address.setFullName(dto.getFullName());
        address.setStreet(dto.getStreet());
        address.setCity(dto.getCity());
        address.setPostalCode(dto.getPostalCode());
        address.setCountry(dto.getCountry());
        address.setLatitude(dto.getLatitude());
        address.setLongitude(dto.getLongitude());
        if (dto.getIsDefault() != null) address.setIsDefault(dto.getIsDefault());

        return toDto(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(String email, Long addressId) {
        User user = getUser(email);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        addressRepository.delete(address);
    }

    private AddressDto toDto(Address a) {
        return AddressDto.builder()
                .id(a.getId()).fullName(a.getFullName())
                .street(a.getStreet()).city(a.getCity())
                .postalCode(a.getPostalCode()).country(a.getCountry())
                .latitude(a.getLatitude()).longitude(a.getLongitude())
                .isDefault(a.getIsDefault()).build();
    }

    private void validateCoordinates(AddressDto dto) {
        Double latitude = dto.getLatitude();
        Double longitude = dto.getLongitude();
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException("Both delivery coordinates must be provided together.");
        }
        if (latitude != null && (latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180)) {
            throw new BadRequestException("Delivery coordinates are outside valid map ranges.");
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
