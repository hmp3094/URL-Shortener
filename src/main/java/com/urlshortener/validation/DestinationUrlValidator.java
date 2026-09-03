package com.urlshortener.validation;

import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Validates a submitted destination URL: only http/https is allowed, and the resolved host must
 * not be a loopback, private (RFC 1918), or link-local address. This service never fetches the
 * destination itself, but a link-preview/unfurl bot that does could otherwise be tricked into
 * reaching an internal address via a short link, so the check is worthwhile as defense in depth.
 */
@Component
public class DestinationUrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }

        URI uri = parse(url);

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("URL scheme must be http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must include a host");
        }

        checkHostIsNotDisallowed(host);
    }

    private URI parse(String url) {
        try {
            URI uri = new URI(url.trim());
            if (!uri.isAbsolute()) {
                throw new InvalidUrlException("URL must be absolute (include a scheme)");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not well-formed: " + e.getMessage());
        }
    }

    private void checkHostIsNotDisallowed(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(IDN.toASCII(host));
        } catch (UnknownHostException e) {
            throw new InvalidUrlException("URL host could not be resolved: " + host);
        }

        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()
                    || address.isMulticastAddress()) {
                throw new InvalidUrlException("URL targets a disallowed private/loopback/link-local address");
            }
        }
    }
}
