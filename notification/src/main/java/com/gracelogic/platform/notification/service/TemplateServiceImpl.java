package com.gracelogic.platform.notification.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.gracelogic.platform.db.dto.EntityListResponse;
import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.dictionary.service.DictionaryService;
import com.gracelogic.platform.notification.dto.Content;
import com.gracelogic.platform.notification.dto.TemplateDTO;
import com.gracelogic.platform.notification.model.Template;
import com.gracelogic.platform.notification.model.TemplateType;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class TemplateServiceImpl implements TemplateService {
    @Autowired
    private IdObjectService idObjectService;

    @Autowired
    private DictionaryService ds;

    private class TemplateKey {
        private UUID templateTypeId;
        private String locale;

        public UUID getTemplateTypeId() {
            return templateTypeId;
        }

        public void setTemplateTypeId(UUID templateTypeId) {
            this.templateTypeId = templateTypeId;
        }

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        public TemplateKey(UUID templateTypeId, String locale) {
            this.templateTypeId = templateTypeId;
            this.locale = locale;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TemplateKey)) return false;

            TemplateKey that = (TemplateKey) o;

            if (!getTemplateTypeId().equals(that.getTemplateTypeId())) return false;
            return StringUtils.equalsIgnoreCase(getLocale(), that.getLocale());
        }

        @Override
        public int hashCode() {
            int result = getTemplateTypeId().hashCode();
            result = 31 * result + getLocale().hashCode();
            return result;
        }
    }

    private Map<TemplateKey, Template> cache = ExpiringMap.builder()
            .expiration(30, TimeUnit.SECONDS)
            .entryLoader(key -> findTemplate((TemplateKey) key))
            .build();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Template saveTemplate(TemplateDTO dto) throws ObjectNotFoundException {
        Template template;
        if (dto.getId() != null) {
            template = idObjectService.getObjectById(Template.class, dto.getId());
            if (template == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            template = new Template();
        }

        template.setName(dto.getName());
        template.setTitle(dto.getTitle());
        template.setBody(dto.getBody());
        template.setLocale(dto.getLocale());
        template.setTemplateType(ds.get(TemplateType.class, dto.getTemplateTypeId()));

        return idObjectService.save(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(UUID id) {
        idObjectService.delete(Template.class, id);
    }

    @Override
    public TemplateDTO getTemplate(UUID id) throws ObjectNotFoundException {
        Template entity = idObjectService.getObjectById(Template.class, id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }

        return TemplateDTO.prepare(entity);
    }

    @Override
    public EntityListResponse<TemplateDTO> getTemplatesPaged(String name, UUID templateTypeId, boolean enrich,
                                                             Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String fetches = "";
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<String, Object>();

        if (!StringUtils.isEmpty(name)) {
            params.put("name", "%%" + StringUtils.lowerCase(name) + "%%");
            cause += " and lower(el.name) like :name";
        }

        if (templateTypeId != null) {
            params.put("templateTypeId", templateTypeId);
            cause += " and el.templateType.id=:templateTypeId";
        }


        int totalCount = idObjectService.getCount(Template.class, null, countFetches, cause, params);

        EntityListResponse<TemplateDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<Template> items = idObjectService.getList(Template.class, fetches, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);
        for (Template e : items) {
            TemplateDTO el = TemplateDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    public Content buildFromTemplate(UUID templateTypeId, Locale locale, Map<String, String> params) {
        Template template = cache.get(new TemplateKey(templateTypeId, locale.toString()));

        String titleTemplate;
        String bodyTemplate;
        if (template != null) {
            titleTemplate = template.getTitle();
            bodyTemplate = template.getBody();
        }
        else {
            //Template not found - build raw params template
            titleTemplate = "Title";
            StringBuilder sb = new StringBuilder();
            for (String param : params.keySet()) {
                sb.append(param).append("=").append(params.get(param)).append("\n");
            }
            bodyTemplate = sb.toString();
        }

        Content content = new Content();
        MustacheFactory mf = new DefaultMustacheFactory();
        if (bodyTemplate != null) {
            Mustache bodyMustache = mf.compile(new StringReader(bodyTemplate), templateTypeId.toString() + locale.toString() + "body");
            content.setBody(bodyMustache.execute(new StringWriter(), params).toString());
        }
        if (titleTemplate != null) {
            Mustache titleMustache = mf.compile(new StringReader(titleTemplate), templateTypeId.toString() + locale.toString() + "title");
            content.setTitle(titleMustache.execute(new StringWriter(), params).toString());
        }

        return content;
    }

    private Template findTemplate(TemplateKey key) {
        Map<String, Object> dbParams = new HashMap<>();
        dbParams.put("templateTypeId", key.getTemplateTypeId());
        dbParams.put("locale", key.getLocale());
        dbParams.put("defaultLocale", "*");

        List<Template> templates = idObjectService.getList(Template.class, null, "el.templateType.id=:templateTypeId and (el.locale=:locale or el.locale=:defaultLocale)", dbParams, "el.locale", "DESC", null, 1);
        if (!templates.isEmpty()) {
            return templates.iterator().next();
        }
        return null;
    }
}
