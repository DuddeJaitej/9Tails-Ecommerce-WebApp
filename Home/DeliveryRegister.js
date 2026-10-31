(function () {
    'use strict';

    const form = document.getElementById('deliveryRegisterForm');
    const button = document.getElementById('deliveryRegisterButton');
    const feedback = document.getElementById('deliveryRegisterFeedback');
    const role = document.body.dataset.registrationRole;
    const operator = role === 'WAREHOUSE_OPERATOR';
    const warehouseSelect = document.getElementById('deliveryWarehouse');
    const locationSearchInput = document.getElementById('warehouseLocationSearch');
    const locationSearchButton = document.getElementById('warehouseLocationSearchButton');
    const locationResults = document.getElementById('warehouseLocationResults');
    const selectedLocation = document.getElementById('warehouseSelectedLocation');
    let locationSearchSequence = 0;

    if (!['DELIVERY_AGENT', 'WAREHOUSE_OPERATOR'].includes(role)) {
        feedback.textContent = 'Choose a valid staff registration form and try again.';
        button.disabled = true;
        return;
    }

    if (role === 'DELIVERY_AGENT') {
        window.tapApi.auth.deliveryWarehouses().then(warehouses => {
            warehouseSelect.replaceChildren(new Option(
                warehouses.length ? 'Choose an approved warehouse' : 'No approved warehouses are available',
                ''
            ));
            warehouses.forEach(warehouse => warehouseSelect.add(new Option(
                `${warehouse.name} · ${warehouse.city} ${warehouse.postalCode}`,
                String(warehouse.id)
            )));
            warehouseSelect.disabled = warehouses.length === 0;
            if (!warehouses.length) {
                feedback.textContent = 'No administrator-approved warehouses are available yet.';
            }
        }).catch(error => {
            warehouseSelect.replaceChildren(new Option('Warehouses could not be loaded', ''));
            warehouseSelect.disabled = true;
            feedback.textContent = `Warehouse options could not be loaded: ${error.message}`;
        });
    }

    function updateWarehouseCoordinates(latitude, longitude) {
        document.getElementById('warehouseLatitude').value = latitude.toFixed(6);
        document.getElementById('warehouseLongitude').value = longitude.toFixed(6);
        document.getElementById('warehouseGoogleMapPreview').src =
            window.TapMaps.embedUrl(latitude, longitude);
        const mapLink = document.getElementById('warehouseGoogleMapsLink');
        mapLink.href = window.TapMaps.locationUrl(latitude, longitude);
        mapLink.classList.remove('hidden');
        selectedLocation.textContent = `Selected coordinates: ${latitude.toFixed(6)}, ${longitude.toFixed(6)}. Verify this point in Google Maps.`;
    }

    function setAddressFromSearchResult(result) {
        const address = result.address || {};
        const street = [
            address.house_number,
            address.road || address.pedestrian || address.residential
        ].filter(Boolean).join(' ');
        document.getElementById('warehouseStreet').value =
            (street || result.name || result.display_name || '').slice(0, 255);
        document.getElementById('warehouseCity').value =
            (address.city || address.town || address.village || address.municipality || '').slice(0, 100);
        document.getElementById('warehouseState').value = (address.state || '').slice(0, 100);
        document.getElementById('warehousePostalCode').value =
            (address.postcode || '').replace(/\D/g, '').slice(0, 6);
        locationSearchInput.value = result.display_name || locationSearchInput.value;
        if (!window.TapMaps.validCoordinates(result.lat, result.lon)) {
            selectedLocation.textContent = 'This address result did not contain valid coordinates. Choose another result.';
            return;
        }
        const latitude = Number(result.lat);
        const longitude = Number(result.lon);
        updateWarehouseCoordinates(latitude, longitude);
    }

    async function searchWarehouseLocation() {
        const query = locationSearchInput.value.trim();
        if (query.length < 5) {
            locationResults.textContent = 'Enter a fuller address, including a road or locality, before searching.';
            return;
        }
        const currentSearch = ++locationSearchSequence;
        locationSearchButton.disabled = true;
        locationSearchButton.textContent = 'Searching…';
        locationResults.textContent = 'Searching for matching addresses…';
        try {
            const parameters = new URLSearchParams({
                format: 'jsonv2',
                addressdetails: '1',
                limit: '5',
                countrycodes: 'in',
                q: query
            });
            const response = await fetch(
                `https://nominatim.openstreetmap.org/search?${parameters.toString()}`,
                { headers: { Accept: 'application/json' } }
            );
            if (!response.ok) {
                throw new Error(`Location search failed (HTTP ${response.status}). Please try again.`);
            }
            const results = await response.json();
            if (currentSearch !== locationSearchSequence) return;
            locationResults.replaceChildren();
            if (!Array.isArray(results) || results.length === 0) {
                locationResults.textContent =
                    'No matching address was found. Add the road number, locality, city, and PIN code, then search again.';
                return;
            }
            const heading = document.createElement('p');
            heading.className = 'muted';
            heading.textContent = 'Choose the result that matches the exact road/building location:';
            locationResults.append(heading);
            const list = document.createElement('div');
            list.className = 'warehouse-location-result-list';
            results.forEach(result => {
                const resultButton = document.createElement('button');
                resultButton.type = 'button';
                resultButton.className = 'warehouse-location-result';
                resultButton.textContent = result.display_name || result.name || 'Unnamed location';
                resultButton.addEventListener('click', () => {
                    setAddressFromSearchResult(result);
                    locationResults.replaceChildren();
                });
                list.append(resultButton);
            });
            locationResults.append(list);
        } catch (error) {
            if (currentSearch === locationSearchSequence) {
                locationResults.textContent = error.message
                    || 'Location search could not be completed. Check your connection and try again.';
            }
        } finally {
            if (currentSearch === locationSearchSequence) {
                locationSearchButton.disabled = false;
                locationSearchButton.textContent = 'Search location';
            }
        }
    }

    if (operator) {
        locationSearchButton.addEventListener('click', searchWarehouseLocation);
        locationSearchInput.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                searchWarehouseLocation();
            }
        });
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        button.disabled = true;
        button.textContent = 'Creating account…';
        feedback.textContent = '';
        try {
            if (role === 'WAREHOUSE_OPERATOR'
                    && (!document.getElementById('warehouseLatitude').value
                    || !document.getElementById('warehouseLongitude').value)) {
                throw new Error('Search for the warehouse address and choose the matching result to set its coordinates.');
            }
            const postalCode = operator
                ? document.getElementById('warehousePostalCode').value.trim()
                : '';
            if (role === 'WAREHOUSE_OPERATOR' && !/^[1-9]\d{5}$/.test(postalCode)) {
                throw new Error('Enter a valid six-digit Indian PIN code for the warehouse.');
            }
            const result = await window.tapApi.auth.registerDelivery({
                fullName: document.getElementById('deliveryRegisterName').value.trim(),
                email: document.getElementById('deliveryRegisterEmail').value.trim().toLowerCase(),
                password: document.getElementById('deliveryRegisterPassword').value,
                role,
                deliveryWarehouseId: role === 'DELIVERY_AGENT' ? Number(warehouseSelect.value) : null,
                warehouseName: role === 'WAREHOUSE_OPERATOR'
                    ? document.getElementById('warehouseName').value.trim() : null,
                warehouseStreet: role === 'WAREHOUSE_OPERATOR'
                    ? document.getElementById('warehouseStreet').value.trim() : null,
                warehouseCity: role === 'WAREHOUSE_OPERATOR'
                    ? document.getElementById('warehouseCity').value.trim() : null,
                warehouseState: role === 'WAREHOUSE_OPERATOR'
                    ? document.getElementById('warehouseState').value.trim() : null,
                warehousePostalCode: role === 'WAREHOUSE_OPERATOR'
                    ? postalCode : null,
                warehouseLatitude: role === 'WAREHOUSE_OPERATOR'
                    ? Number(document.getElementById('warehouseLatitude').value) : null,
                warehouseLongitude: role === 'WAREHOUSE_OPERATOR'
                    ? Number(document.getElementById('warehouseLongitude').value) : null
            });
            if (result.role !== role) {
                throw new Error('The server returned a different staff role than requested.');
            }
            const tokenKey = role === 'DELIVERY_AGENT'
                ? 'tap_delivery_agent_jwt'
                : 'tap_warehouse_operator_jwt';
            window.tapApi.setToken(result.token, tokenKey);
            feedback.textContent = role === 'DELIVERY_AGENT'
                ? 'Account created and sent to the selected warehouse operator for approval. Opening your workspace…'
                : 'Warehouse application submitted to the administrator for approval. Opening your workspace…';
            feedback.classList.add('feedback-success');
            window.location.replace('Delivery.html');
        } catch (error) {
            feedback.textContent = error.message || 'Registration failed. Check your details and staff role requirements.';
            feedback.classList.remove('feedback-success');
        } finally {
            button.disabled = false;
            button.textContent = operator ? 'Submit warehouse application' : 'Register delivery account';
        }
    });
}());
