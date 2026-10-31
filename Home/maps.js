(function (global) {
    'use strict';

    function validCoordinates(latitude, longitude) {
        return latitude !== null && latitude !== undefined && String(latitude).trim() !== ''
            && longitude !== null && longitude !== undefined && String(longitude).trim() !== ''
            && Number.isFinite(Number(latitude))
            && Number(latitude) >= -90 && Number(latitude) <= 90
            && Number.isFinite(Number(longitude))
            && Number(longitude) >= -180 && Number(longitude) <= 180;
    }

    function locationUrl(latitude, longitude, query = '') {
        const search = new URLSearchParams();
        search.set('api', '1');
        search.set('query', validCoordinates(latitude, longitude)
            ? `${Number(latitude)},${Number(longitude)}`
            : String(query).trim());
        return `https://www.google.com/maps/search/?${search.toString()}`;
    }

    function embedUrl(latitude, longitude, zoom = 16) {
        if (!validCoordinates(latitude, longitude)) {
            throw new Error('Valid coordinates are required to preview a Google Maps location.');
        }
        const search = new URLSearchParams({
            output: 'embed',
            q: `${Number(latitude)},${Number(longitude)}`,
            z: String(zoom)
        });
        return `https://www.google.com/maps?${search.toString()}`;
    }

    function directionsUrl(origin, destination) {
        if (!destination || !validCoordinates(destination.latitude, destination.longitude)) {
            throw new Error('A valid destination is required to open Google Maps directions.');
        }
        const search = new URLSearchParams({
            api: '1',
            destination: `${Number(destination.latitude)},${Number(destination.longitude)}`,
            travelmode: 'driving'
        });
        if (origin && validCoordinates(origin.latitude, origin.longitude)) {
            search.set('origin', `${Number(origin.latitude)},${Number(origin.longitude)}`);
        }
        return `https://www.google.com/maps/dir/?${search.toString()}`;
    }

    global.TapMaps = Object.freeze({ locationUrl, embedUrl, directionsUrl, validCoordinates });
})(window);
