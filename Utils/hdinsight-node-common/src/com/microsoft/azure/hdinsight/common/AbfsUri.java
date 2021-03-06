/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.hdinsight.common;

import com.microsoft.azuretools.azurecommons.helpers.Nullable;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.UnknownFormatConversionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AbfsUri extends AzureStorageUri {
    public static final String AdlsGen2PathPattern = "^(?<schema>abfss?)://(?<fileSystem>[^/.\\s]+)@(?<accountName>[^/.\\s]+)(\\.)(dfs\\.core\\.windows\\.net)(?<relativePath>(/[-a-zA-Z0-9.~_@:!$'()*+,;=%]+)*/?)$";
    public static final String AdlsGen2RestfulPathPattern = "^(?<schema>https?)://(?<accountName>[^/.\\s]+)(\\.)(dfs\\.core\\.windows\\.net)(/)(?<fileSystem>[^/.\\s]+)(?<relativePath>(/[-a-zA-Z0-9.~_@:!$'()*+,;=%]+)*/?)$";
    public static final Pattern ABFS_URI_PATTERN = Pattern.compile(AdlsGen2PathPattern, Pattern.CASE_INSENSITIVE);
    public static final Pattern HTTP_URI_PATTERN = Pattern.compile(AdlsGen2RestfulPathPattern, Pattern.CASE_INSENSITIVE);

    private final LaterInit<String> fileSystem = new LaterInit<>();
    private final LaterInit<String> accountName = new LaterInit<>();

    private AbfsUri(URI rawUri) {
        super(rawUri);
    }

    public String getFileSystem() {
        return fileSystem.get();
    }

    public String getAccountName() {
        return accountName.get();
    }

    @Override
    AzureStorageUri parseUri(URI encodedUri) {
        return AbfsUri.parse(encodedUri.toString());
    }

    @Override
    public URI getUri() {
        return URI.create(String.format("abfs://%s@%s.dfs.core.windows.net%s",
                getFileSystem(), getAccountName(), getRawPath()));
    }

    @Override
    public URL getUrl() {
        try {
            return URI.create(String.format("https://%s.dfs.core.windows.net/%s%s",
                    getAccountName(), getFileSystem(), getRawPath())).toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    // get subPath starting without "/" except when subPath is empty
    public String getDirectoryParam() {
        return getPath().length() == 0 || getPath().equals("/")
                ? "/"
                : getPath().substring(1);
    }

    // We assume rawUri is already encoded
    public static AbfsUri parse(final String rawUri) {
        Matcher matcher;
        if (StringUtils.startsWithIgnoreCase(rawUri, "abfs")) {
            matcher = ABFS_URI_PATTERN.matcher(rawUri);
        } else if (StringUtils.startsWithIgnoreCase(rawUri, "http")) {
            matcher = HTTP_URI_PATTERN.matcher(rawUri);
        } else {
            throw new UnknownFormatConversionException("Unsupported ADLS Gen2 URI Scheme: " + rawUri);
        }

        if (matcher.matches()) {
            AbfsUri abfsUri = new AbfsUri(URI.create(rawUri));
            abfsUri.accountName.set(matcher.group("accountName"));
            abfsUri.fileSystem.set(matcher.group("fileSystem"));
            abfsUri.path.set(URI.create(matcher.group("relativePath")));
            return abfsUri;
        }

        throw new UnknownFormatConversionException("Unmatched ADLS Gen2 URI: " + rawUri);
    }

    public static boolean isType(@Nullable final String uri) {
        return uri != null && (ABFS_URI_PATTERN.matcher(uri).matches() || HTTP_URI_PATTERN.matcher(uri).matches());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFileSystem(), getAccountName(), getPath());
    }
}
