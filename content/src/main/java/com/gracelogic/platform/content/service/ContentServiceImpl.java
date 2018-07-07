package com.gracelogic.platform.content.service;

import com.gracelogic.platform.content.dao.ContentDao;
import com.gracelogic.platform.content.dto.ElementDTO;
import com.gracelogic.platform.content.dto.SectionDTO;
import com.gracelogic.platform.content.dto.SectionPatternDTO;
import com.gracelogic.platform.content.dto.SectionPatternFieldDTO;
import com.gracelogic.platform.content.model.Element;
import com.gracelogic.platform.content.model.Section;
import com.gracelogic.platform.content.model.SectionPattern;
import com.gracelogic.platform.content.model.SectionPatternField;
import com.gracelogic.platform.db.dto.EntityListResponse;
import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.user.service.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ContentServiceImpl implements ContentService {
    @Autowired
    private IdObjectService idObjectService;

    @Autowired
    private ContentDao contentDao;

    @Override
    public List<SectionDTO> getSectionsHierarchically(UUID parentId, boolean onlyActive) {
        SectionDTO root = new SectionDTO();
        root.setId(null);

        //Key - childId, Category - parent
        HashMap<UUID, SectionDTO> processedSections = new HashMap<UUID, SectionDTO>();

        List<Section> sections = idObjectService.getList(Section.class);

        for (Section section : sections) {
            if (onlyActive && !section.getActive()) {
                continue;
            }

            SectionDTO currentSection = SectionDTO.prepare(section);

            //FIND MYSELF
            if (processedSections.containsKey(currentSection.getId())) {
                SectionDTO myselfParent = processedSections.get(currentSection.getId());
                if (currentSection.getParentId() != null && !myselfParent.getId().equals(currentSection.getParentId())) { //Значит, что категория привязана к руту
                    for (SectionDTO myself : myselfParent.getChildren()) {
                        if (myself.getId().equals(currentSection.getId())) {
                            currentSection.setChildren(myself.getChildren());
                        }
                    }
                    myselfParent.removeChild(currentSection.getId());
                    processedSections.remove(currentSection.getId());

                    //Ищем или создаём нормального родителя
                    UUID parentCategoryId = section.getParent().getId();
                    if (processedSections.containsKey(parentCategoryId)) {
                        SectionDTO grandParentCategory = processedSections.get(parentCategoryId);
                        for (SectionDTO parentCategory : grandParentCategory.getChildren()) {
                            if (parentCategory.getId().equals(parentCategoryId)) {
                                parentCategory.addChild(currentSection);
                                processedSections.put(currentSection.getId(), parentCategory);
                            }
                        }
                    } else {
                        Section parent = null;
                        for (Section s : sections) {
                            if (s.getId().equals(section.getParent().getId())) {
                                parent = s;
                                break;
                            }
                        }

                        SectionDTO parentSection = SectionDTO.prepare(parent);

                        root.addChild(parentSection);
                        processedSections.put(parentSection.getId(), root);

                        parentSection.addChild(currentSection);
                        processedSections.put(currentSection.getId(), parentSection);
                    }
                }
            } else {
                //FIND PARENT
                if (section.getParent() != null) {
                    Section parent = null;
                    for (Section s : sections) {
                        if (s.getId().equals(section.getParent().getId())) {
                            parent = s;
                            break;
                        }
                    }

                    SectionDTO parentSection = SectionDTO.prepare(parent);
                    if (processedSections.containsKey(parentSection.getId())) {
                        SectionDTO grandParentSection = processedSections.get(parentSection.getId());

                        for (SectionDTO extParentSection : grandParentSection.getChildren()) {
                            if (extParentSection.getId().equals(parentSection.getId())) {
                                extParentSection.addChild(currentSection);

                                processedSections.put(currentSection.getId(), extParentSection);
                                break;
                            }
                        }
                    } else {
                        root.addChild(parentSection);
                        processedSections.put(parentSection.getId(), root);

                        parentSection.addChild(currentSection);
                        processedSections.put(currentSection.getId(), parentSection);
                    }
                } else {
                    root.addChild(currentSection);
                    processedSections.put(currentSection.getId(), root);
                }

            }
        }
        //Sort by pos
        for (UUID key : processedSections.keySet()) {
            SectionDTO sectionDTO = processedSections.get(key);
            Collections.sort(sectionDTO.getChildren(), new Comparator<SectionDTO>() {
                @Override
                public int compare(final SectionDTO o1, final SectionDTO o2) {
                    return o1 == null ? 1 : o2 == null ? -1 : o1.getSortOrder().compareTo(o2.getSortOrder());
                }
            });
        }

        if (parentId == null) {
            return root.getChildren();
        } else {
            if (processedSections.containsKey(parentId)) {
                SectionDTO grandParentSection = processedSections.get(parentId);
                for (SectionDTO parentSection : grandParentSection.getChildren()) {
                    if (parentSection.getId().equals(parentId)) {
                        return parentSection.getChildren();
                    }
                }
            }
        }
        return new LinkedList<SectionDTO>();
    }

    @Override
    public SectionDTO getSection(UUID id) throws ObjectNotFoundException {
        Section section = idObjectService.getObjectById(Section.class, id);
        if (section == null) {
            throw new ObjectNotFoundException();
        }

        return SectionDTO.prepare(section);
    }

    @Override
    public EntityListResponse<ElementDTO> getElementsPaged(String name, Collection<UUID> sectionIds, Boolean active, Date validOnDate, Map<String, String> fields, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        if (!StringUtils.isEmpty(sortField)) {
            //Т.к. в данном методе запрос используется нативный и требуется сохранить единообразие - транслируем название jpa полей в нативные sql
            if (StringUtils.equalsIgnoreCase(sortField, "el.id")) {
                sortField = "id";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.created")) {
                sortField = "created_dt";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.changed")) {
                sortField = "changed_dt";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.elementDt")) {
                sortField = "element_dt";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.startDt")) {
                sortField = "start_dt";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.endDt")) {
                sortField = "end_dt";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.name")) {
                sortField = "name";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.sortOrder")) {
                sortField = "sort_order";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.section.id")) {
                sortField = "section_id";
            }
            else if (StringUtils.equalsIgnoreCase(sortField, "el.active")) {
                sortField = "is_active";
            }
            else if (StringUtils.startsWithIgnoreCase(sortField, "el.fields")) {
                sortField = sortField.substring("el.".length());
            }
        }

        int totalCount = contentDao.getElementsCount(name, sectionIds, active, validOnDate, fields);
        int totalPages = ((totalCount / count));
        if (totalPages %2 != 0) {totalPages++;};
        int startRecord = page != null ? (page * count) - count : start;

        EntityListResponse<ElementDTO> entityListResponse = new EntityListResponse<ElementDTO>();
        entityListResponse.setEntity("element");
        entityListResponse.setPage(page);
        entityListResponse.setPages(totalPages);
        entityListResponse.setTotalCount(totalCount);

        List<Element> items = contentDao.getElements(name, sectionIds, active, validOnDate, fields, sortField, sortDir, startRecord, count);
        entityListResponse.setPartCount(items.size());
        for (Element e : items) {
            ElementDTO el = ElementDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Element saveElement(ElementDTO dto) throws ObjectNotFoundException {
        Element element;
        if (dto.getId() != null) {
            element = idObjectService.getObjectById(Element.class, dto.getId());
            if (element == null) {
                throw new ObjectNotFoundException();
            }
        }
        else {
            element = new Element();
        }

        element.setName(dto.getName());
        element.setActive(dto.getActive());
        element.setSortOrder(dto.getSortOrder());
        element.setStartDt(dto.getStartDt());
        element.setEndDt(dto.getEndDt());
        element.setElementDt(dto.getElementDt());
        element.setSection(idObjectService.getObjectById(Section.class, dto.getSectionId()));
        element.setFields(JsonUtils.mapToJson(dto.getFields()));

        return idObjectService.save(element);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteElement(UUID id) {
        idObjectService.delete(Element.class, id);
    }

    @Override
    public SectionPatternDTO getSectionPattern(UUID sectionPatternId) throws ObjectNotFoundException {
        SectionPattern sectionPattern = idObjectService.getObjectById(SectionPattern.class, sectionPatternId);
        if (sectionPattern == null) {
            throw new ObjectNotFoundException();
        }

        SectionPatternDTO dto = SectionPatternDTO.prepare(sectionPattern);

        Map<String, Object> params = new HashMap<>();
        params.put("sectionPatternId", sectionPatternId);
        List<SectionPatternField> fields = idObjectService.getList(SectionPatternField.class, null, "el.sectionPattern.id=:sectionPatternId", params, null, null, null, null);
        for (SectionPatternField field : fields) {
            dto.getFields().add(SectionPatternFieldDTO.prepare(field));
        }

        return dto;
    }

    @Override
    public SectionPatternDTO getSectionPatternBySection(UUID sectionId) throws ObjectNotFoundException {
        Section section = idObjectService.getObjectById(Section.class, sectionId);
        if (section == null) {
            throw new ObjectNotFoundException();
        }
        return getSectionPattern(section.getSectionPattern().getId());
    }

    @Override
    public ElementDTO getElement(UUID id, boolean includeSectionPattern) throws ObjectNotFoundException {
        Element element = idObjectService.getObjectById(Element.class, includeSectionPattern ? "left join fetch el.section" : null, id);
        if (element == null) {
            throw new ObjectNotFoundException();
        }

        ElementDTO dto = ElementDTO.prepare(element);
        if (includeSectionPattern) {
            dto.setSectionPattern(getSectionPattern(element.getSection().getSectionPattern().getId()));
        }
        return dto;
    }


}
