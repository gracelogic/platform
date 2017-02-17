package com.gracelogic.platform.filestorage.service;

import com.gracelogic.platform.db.dto.EntityListResponse;
import com.gracelogic.platform.filestorage.dto.StoredFileDTO;
import com.gracelogic.platform.filestorage.exception.StoredFileDataUnavailableException;
import com.gracelogic.platform.filestorage.exception.UnsupportedStoreModeException;
import com.gracelogic.platform.filestorage.model.StoredFile;
import com.gracelogic.platform.user.exception.ObjectNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;

public interface FileStorageService {
    StoredFile createStoredFile(UUID storeModeId, UUID referenceObjectId, InputStream is, String extension, String meta) throws UnsupportedStoreModeException, IOException;

    void updateStoredFile(UUID id, InputStream is, String extension, String meta) throws ObjectNotFoundException, IOException;

    EntityListResponse<StoredFileDTO> getStoredFilesPaged(UUID referenceObjectId, Boolean dataAvailable, Collection<UUID> storeModeIds, boolean enrich, Integer count, Integer page, Integer start, String sortField, String sortDir);

    byte[] getStoredFileData(StoredFile storedFile) throws UnsupportedStoreModeException, StoredFileDataUnavailableException, IOException;

    void deleteStoredFile(UUID id, boolean withContent);

    String buildLocalStoringPath(UUID id, UUID referenceObjectId, String extension);
}