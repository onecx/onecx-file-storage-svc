package org.tkit.onecx.file.storage.rs.external.v1.mappers;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileMetadataResponseDTOV1;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@Mapper()
public interface MetadataMapper {

    @Mapping(target = "sizeUnit", ignore = true)
    @Mapping(target = "storage", ignore = true)
    @Mapping(target = "size", expression = "java(head.contentLength())")
    @Mapping(target = "type", expression = "java(head.contentType())")
    FileMetadataResponseDTOV1 map(HeadObjectResponse head);

    @AfterMapping
    default void setDefaultValues(HeadObjectResponse source,
            @MappingTarget FileMetadataResponseDTOV1 target) {
        target.setSizeUnit("BYTES");
        target.setStorage("rustfs");
    }
}
