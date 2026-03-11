package org.tkit.onecx.file.storage.rs.external.v1.mappers;

import java.time.OffsetDateTime;

import org.mapstruct.Mapper;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.file.storage.rs.external.v1.model.PresignedUrlResponseDTOV1;

@Mapper(uses = OffsetDateTimeMapper.class)
public interface PresignedUrlMapper {

    PresignedUrlResponseDTOV1 map(String url, OffsetDateTime expiration);
}
