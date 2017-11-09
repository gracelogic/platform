package com.gracelogic.platform.market.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gracelogic.platform.db.dto.IdObjectDTO;
import com.gracelogic.platform.db.dto.JsonDateDeserializer;
import com.gracelogic.platform.db.dto.JsonDateSerializer;
import com.gracelogic.platform.market.model.Product;

import java.util.Date;
import java.util.UUID;

public class ProductDTO extends IdObjectDTO {
    private String name;
    private UUID referenceObjectId;
    private UUID productTypeId;
    private String productTypeName;
    private Boolean active;

    //Transient field for OrderProduct
    @JsonSerialize(using = JsonDateSerializer.class, include=JsonSerialize.Inclusion.NON_NULL)
    @JsonDeserialize(using = JsonDateDeserializer.class)
    private Date lifecycleExpiration;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getReferenceObjectId() {
        return referenceObjectId;
    }

    public void setReferenceObjectId(UUID referenceObjectId) {
        this.referenceObjectId = referenceObjectId;
    }

    public UUID getProductTypeId() {
        return productTypeId;
    }

    public void setProductTypeId(UUID productTypeId) {
        this.productTypeId = productTypeId;
    }

    public String getProductTypeName() {
        return productTypeName;
    }

    public void setProductTypeName(String productTypeName) {
        this.productTypeName = productTypeName;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Date getLifecycleExpiration() {
        return lifecycleExpiration;
    }

    public void setLifecycleExpiration(Date lifecycleExpiration) {
        this.lifecycleExpiration = lifecycleExpiration;
    }

    public static ProductDTO prepare(Product model) {
        ProductDTO dto = new ProductDTO();
        IdObjectDTO.prepare(dto, model);

        if (model.getProductType() != null) {
            dto.setProductTypeId(model.getProductType().getId());
        }
        dto.setName(model.getName());
        dto.setActive(model.getActive());
        dto.setReferenceObjectId(model.getReferenceObjectId());
        return dto;
    }

    public static ProductDTO enrich(ProductDTO dto, Product model) {
        if (model.getProductType() != null) {
            dto.setProductTypeName(model.getProductType().getName());
        }

        return dto;
    }
}