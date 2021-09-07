package com.gracelogic.platform.survey.service;

import com.gracelogic.platform.db.dto.EntityListResponse;
import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.db.model.IdObject;
import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.dictionary.service.DictionaryService;
import com.gracelogic.platform.filestorage.model.StoredFile;
import com.gracelogic.platform.localization.service.LocaleHolder;
import com.gracelogic.platform.survey.dto.admin.*;
import com.gracelogic.platform.survey.dto.user.*;
import com.gracelogic.platform.survey.exception.*;
import com.gracelogic.platform.survey.model.*;
import com.gracelogic.platform.user.dto.AuthorizedUser;
import com.gracelogic.platform.user.exception.ForbiddenException;
import com.gracelogic.platform.user.model.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SurveyServiceImpl implements SurveyService {

    @Autowired
    private IdObjectService idObjectService;

    @Autowired
    private DictionaryService ds;

    @Autowired
    @Qualifier("surveyMessageSource")
    private ResourceBundleMessageSource messageSource;

    private enum LogicTriggerCheckItem {
        PAGE,
        QUESTION,
        ANSWER,
    }

    /**
     * Represents result set as UUID HashMap
     * @param list ResultSet
     * @param <T>
     */
    private static <T extends IdObject<UUID>> HashMap<UUID, T> asUUIDHashMap(List<T> list) {
        HashMap<UUID, T> hashMap = new HashMap<>();
        for (T t : list) hashMap.put(t.getId(), t);
        return hashMap;
    }

    private static HashMap<UUID, List<SurveyAnswerVariant>> asListAnswerVariantHashMap(List<SurveyAnswerVariant> list) {
        HashMap<UUID, List<SurveyAnswerVariant>> hashMap = new HashMap<>();

        for (SurveyAnswerVariant variant : list) {
            List<SurveyAnswerVariant> variantList = hashMap.get(variant.getSurveyQuestion().getId());
            if (variantList != null) {
                variantList.add(variant);
                continue;
            }
            variantList = new LinkedList<>();
            variantList.add(variant);
            hashMap.put(variant.getSurveyQuestion().getId(), variantList);
        }

        return hashMap;
    }

    @Override
    public String exportCatalogItems(UUID catalogId) throws ObjectNotFoundException {
        if (idObjectService.getObjectById(SurveyAnswerVariantCatalog.class, catalogId) == null)
            throw new ObjectNotFoundException();

        HashMap<String, Object> params = new HashMap<>();
        params.put("catalogId", catalogId);
        List<SurveyAnswerVariantCatalogItem> items = idObjectService.getList(SurveyAnswerVariantCatalogItem.class,
                null, "el.catalog.id = :catalogId",
                params, null, null, null);
        StringBuilder result = new StringBuilder();
        for (SurveyAnswerVariantCatalogItem item : items) {
            result.append(item.getText()).append('\n');
        }
        result.deleteCharAt(result.length()-1);

        return result.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void importCatalogItems(ImportCatalogItemsDTO dto)
            throws ObjectNotFoundException {

        if (idObjectService.getObjectById(SurveyAnswerVariantCatalog.class, dto.getCatalogId()) == null)
            throw new ObjectNotFoundException();

        for (String str : dto.getItems()) {
            if (StringUtils.isBlank(str)) continue;
            SurveyAnswerVariantCatalogItemDTO newItem = new SurveyAnswerVariantCatalogItemDTO();
            newItem.setCatalogId(dto.getCatalogId());
            newItem.setText(str);
            saveCatalogItem(newItem);
        }
    }

    public String exportResults(UUID surveyId) throws ObjectNotFoundException, InternalErrorException {
        Survey survey = idObjectService.getObjectById(Survey.class, surveyId);
        if (survey == null) throw new ObjectNotFoundException();

        char separator = ';';
        StringBuilder pattern = new StringBuilder();

        Map<String, Object> params = new HashMap<>();
        params.put("surveyId", surveyId);

        // get all questions by this survey
        List<SurveyQuestion> sortedQuestions = idObjectService.getList(SurveyQuestion.class,
                "left join el.surveyPage sp",
                "sp.survey.id = :surveyId", params, "el.questionIndex, sp.pageIndex ASC", null, null);

        HashMap<UUID, SurveyQuestion> surveyQuestions = asUUIDHashMap(sortedQuestions);

        for (SurveyQuestion question : sortedQuestions) {
            pattern.append(question.getText()).append(separator);
        }
        pattern.deleteCharAt(pattern.length()-1).append('\n');

        // get suitable sessions
        List<SurveySession> sessionsList = idObjectService.getList(SurveySession.class, null,
                "el.previewSession = false and el.ended != null and el.survey.id = :surveyId ",
                params, "el.changed", null, null, null);

        HashMap<UUID, SurveySession> surveySessionHashMap = asUUIDHashMap(sessionsList);

        if (sessionsList.size() == 0) return pattern.toString();


        params.clear();

        HashSet<UUID> sessionIds = new HashSet<>();
        for (SurveySession session : sessionsList) sessionIds.add(session.getId());
        params.put("surveySessionIds", sessionIds);

        List<SurveyQuestionAnswer> answersList = idObjectService.getList(SurveyQuestionAnswer.class, null,
                "el.surveySession.id in (:surveySessionIds)", params, null, null, null, null);

        if (answersList.size() == 0) return pattern.toString();

        HashSet<UUID> answerVariantIds = new HashSet<>();
        for (SurveyQuestionAnswer answer : answersList) {
            if (answer.getAnswerVariant() != null)
                answerVariantIds.add(answer.getAnswerVariant().getId());
        }
        params.clear();

        HashMap<UUID, SurveyAnswerVariant> surveyAnswerVariants = new HashMap<>();
        if (answerVariantIds.size() != 0) {
            params.put("answerVariantIds", answerVariantIds);
            surveyAnswerVariants = asUUIDHashMap(idObjectService.getList(SurveyAnswerVariant.class, null,
                    "el.id in (:answerVariantIds)", params, null, null, null, null));
            params.clear();
        }

        TreeMap<SurveySession, HashMap<UUID, List<SurveyQuestionAnswer>>> answersBySessionAndQuestion = new TreeMap<>(new Comparator<SurveySession>() {
            @Override
            public int compare(SurveySession o1, SurveySession o2) {
                return o1.getCreated().compareTo(o2.getCreated());
            }
        });

        //HashMap<UUID, HashMap<UUID, List<SurveyQuestionAnswer>>> answersBySessionAndQuestion = new HashMap<>();
        for (SurveyQuestionAnswer answer : answersList) {
            // if no such session
            if (!answersBySessionAndQuestion.containsKey(surveySessionHashMap.get(answer.getSurveySession().getId()))) {

                HashMap<UUID, List<SurveyQuestionAnswer>> answersByQuestion = new HashMap<>();
                List<SurveyQuestionAnswer> questionAnswers = new ArrayList<>();
                questionAnswers.add(answer);

                answersByQuestion.put(answer.getQuestion().getId(), questionAnswers);
                answersBySessionAndQuestion.put(surveySessionHashMap.get(answer.getSurveySession().getId()), answersByQuestion);
                continue;
            }

            // if has session, but don't have such question
            if (!answersBySessionAndQuestion.get(
                    surveySessionHashMap.get(answer.getSurveySession().getId())).containsKey(answer.getQuestion().getId())) {
                List<SurveyQuestionAnswer> questionAnswers = new ArrayList<>();
                questionAnswers.add(answer);

                answersBySessionAndQuestion.get(surveySessionHashMap.get(answer.getSurveySession().getId())).put(answer.getQuestion().getId(), questionAnswers);
                continue;
            }

            answersBySessionAndQuestion.get(surveySessionHashMap.get(answer.getSurveySession().getId())).get(answer.getQuestion().getId()).add(answer);
        }

        StringBuilder resultsBuilder = new StringBuilder();
        for (Map.Entry<SurveySession, HashMap<UUID, List<SurveyQuestionAnswer>>> entry : answersBySessionAndQuestion.entrySet()) {

            HashMap<SurveyQuestion, String> answersAsString = new HashMap<>();
            for (Map.Entry<UUID, List<SurveyQuestionAnswer>> questionAnswers : entry.getValue().entrySet()) {
                SurveyQuestion surveyQuestion = surveyQuestions.get(questionAnswers.getKey());
                List<SurveyQuestionAnswer> answers = questionAnswers.getValue();

                // text values
                if (surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.TEXT_SINGLE_LINE.getValue()) ||
                        surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.TEXT_MULTILINE.getValue()) ||
                        surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.RATING_SCALE.getValue())) {
                    answersAsString.put(surveyQuestion, answers.get(0).getText());
                    continue;
                }

                // single answer variant values
                if (surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.RADIOBUTTON.getValue()) ||
                        surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.COMBOBOX.getValue())) {
                    SurveyQuestionAnswer questionAnswer = answers.get(0);

                    if (questionAnswer.getAnswerVariant() == null) {
                        // should never happen, but anyway
                        throw new InternalErrorException("Question answer is not valid, please contact app developer. Survey answer:\n"
                                + questionAnswer.toString() + "\nSurvey question:\n" + surveyQuestion.toString());
                    }

                    SurveyAnswerVariant answerVariant = surveyAnswerVariants.get(questionAnswer.getAnswerVariant().getId());

                    String text = answerVariant.isCustomVariant() ? questionAnswer.getText() : answerVariant.getText();
                    answersAsString.put(surveyQuestion, text);
                    continue;
                }

                // multiple answer variant values
                if (surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.CHECKBOX.getValue()) ||
                        surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.LISTBOX.getValue())) {
                    StringBuilder multiple = new StringBuilder().append("\"");

                    for (SurveyQuestionAnswer answer : answers) {
                        if (answer.getAnswerVariant() == null) {
                            throw new InternalErrorException("Question answer is not valid, please contact app developer. Survey answer:\n"
                                    + answer.toString() + "\nSurvey question:\n" + surveyQuestion.toString());
                        }

                        SurveyAnswerVariant answerVariant = surveyAnswerVariants.get(answer.getAnswerVariant().getId());
                        String text = answer.getText() != null ? answer.getText() : answerVariant.getText();
                        multiple.append(text).append(separator);
                    }
                    multiple.deleteCharAt(multiple.length()-1).append('\"');
                    answersAsString.put(surveyQuestion, multiple.toString());
                }

                // matrices
                if (surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.MATRIX_RADIOBUTTON.getValue()) ||
                    surveyQuestion.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.MATRIX_CHECKBOX.getValue())) {
                    StringBuilder multiple = new StringBuilder().append("\"");

                    for (SurveyQuestionAnswer answer : answers) {
                        SurveyAnswerVariant answerVariant = answer.getAnswerVariant() != null ? surveyAnswerVariants.get(answer.getAnswerVariant().getId()) : null;
                        String columnString = surveyQuestion.getMatrixColumns()[answer.getSelectedMatrixColumn()];
                        if (answerVariant != null) {
                            String customText = answer.getText();
                            multiple.append(customText).append(separator).append(columnString).append(separator);
                        } else {
                            String rowText = surveyQuestion.getMatrixRows()[answer.getSelectedMatrixRow()];
                            multiple.append(rowText).append(separator).append(columnString).append(separator);
                        }
                    }
                    multiple.deleteCharAt(multiple.length()-1).append('\"');
                    answersAsString.put(surveyQuestion, multiple.toString());
                }
            }

            StringBuilder singleResult = new StringBuilder();
            for (SurveyQuestion question : sortedQuestions) {
                String cell = answersAsString.containsKey(question) ?
                                answersAsString.get(question).replace("\n", " ").replace("\r", " ") : "";
                boolean alreadyStartsWithComma = cell.startsWith("\"");
                singleResult.append(!alreadyStartsWithComma ? "\"" : "").append(cell).append(!alreadyStartsWithComma ? "\"" : "").append(separator);
            }

            singleResult.deleteCharAt(singleResult.length()-1).append('\n');
            resultsBuilder.append(singleResult);
        }

        return pattern.append(resultsBuilder).toString();
    }

    private SurveyIntroductionDTO getSurveyIntroduction(Survey survey)
            throws ObjectNotFoundException, ForbiddenException {
        if (survey == null) throw new ObjectNotFoundException();
        if (!survey.isActive()) throw new ForbiddenException();

        return new SurveyIntroductionDTO(survey);
    }

    @Override
    public SurveyIntroductionDTO getSurveyIntroduction(UUID surveyId)
            throws ObjectNotFoundException, ForbiddenException {
        Survey survey = idObjectService.getObjectById(Survey.class, surveyId);
        return getSurveyIntroduction(survey);
    }

    @Override
    public SurveyIntroductionDTO getSurveyIntroductionByExternalId(String externalId)
            throws ObjectNotFoundException, ForbiddenException {
        Map<String, Object> params = new HashMap<>();
        params.put("externalId", externalId);

        List<Survey> surveys = idObjectService.getList(Survey.class, null, "el.externalId = :externalId",
                params, null, null, null);

        if (surveys.isEmpty()) {
            throw new ObjectNotFoundException();
        }

        return getSurveyIntroduction(surveys.iterator().next());
    }

    private SurveyInteractionDTO startSurveyPreview(Survey survey, AuthorizedUser user, String ipAddress)
            throws ObjectNotFoundException {
        if (survey == null) {
            throw new ObjectNotFoundException();
        }

        Date now = new Date();

        SurveySession surveySession = new SurveySession();
        surveySession.setStarted(now);
        surveySession.setLastVisitIP(ipAddress);
        if (user != null) {
            surveySession.setUser(idObjectService.getObjectById(User.class, user.getId()));
        }

        if (survey.getTimeLimit() != null && survey.getTimeLimit() > 0) {
            surveySession.setExpirationDate(new Date(now.getTime() + survey.getTimeLimit()));
        }

        Integer[] pageVisitHistory = new Integer[1];
        pageVisitHistory[0] = 0;
        surveySession.setPageVisitHistory(pageVisitHistory);
        surveySession.setLink(survey.getLink());
        surveySession.setPreviewSession(true);
        surveySession.setConclusion(survey.getConclusion());
        surveySession.setSurvey(survey);
        idObjectService.save(surveySession);

        SurveyInteractionDTO surveyInteractionDTO = new SurveyInteractionDTO();
        surveyInteractionDTO.setSurveySessionId(surveySession.getId());
        surveyInteractionDTO.setSurveyPage(getSurveyPage(surveySession, 0));
        return surveyInteractionDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyInteractionDTO startSurveyPreview(UUID surveyId, AuthorizedUser user, String ipAddress)
            throws ObjectNotFoundException {
        return startSurveyPreview(idObjectService.getObjectById(Survey.class, surveyId), user, ipAddress);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyInteractionDTO startSurveyPreviewByExternalId(String externalSurveyId, AuthorizedUser user, String ipAddress)
            throws ObjectNotFoundException {
        Map<String, Object> params = new HashMap<>();
        params.put("externalId", externalSurveyId);

        List<Survey> surveys = idObjectService.getList(Survey.class, null, "el.externalId = :externalId",
                params, null, null, null);

        if (surveys.isEmpty()) {
            throw new ObjectNotFoundException();
        }

        return startSurveyPreview(surveys.iterator().next(), user, ipAddress);
    }

    private SurveyInteractionDTO startSurvey(Survey survey, AuthorizedUser user, String ipAddress)
            throws ObjectNotFoundException, RespondentLimitException, ForbiddenException, MaxAttemptsException {
        if (survey == null) throw new ObjectNotFoundException();

        Date now = new Date();

        if (!survey.isActive()) throw new ForbiddenException();

        if (survey.getExpirationDate() != null && survey.getExpirationDate().before(now))
            throw new ForbiddenException();

        if (survey.getStartDate() != null && survey.getStartDate().after(now))
            throw new ForbiddenException();

        if (user == null && survey.getSurveyParticipationType().getId().equals(DataConstants.ParticipationTypes.AUTHORIZATION_REQUIRED.getValue())) {
            throw new ForbiddenException();
        }

        // Check max attempts
        if (survey.getMaxAttempts() != null && survey.getMaxAttempts() > 0) {
            Map<String, Object> params = new HashMap<>();
            String cause = "el.survey.id=:surveyId ";
            params.put("surveyId", survey.getId());

            if (survey.getSurveyParticipationType().getId().equals(DataConstants.ParticipationTypes.AUTHORIZATION_REQUIRED.getValue())) {
                cause += "and el.user.id=:userId ";
                params.put("userId", user.getId());
            }

            if (survey.getSurveyParticipationType().getId().equals(DataConstants.ParticipationTypes.IP_LIMITED.getValue()) ||
                    survey.getSurveyParticipationType().getId().equals(DataConstants.ParticipationTypes.COOKIE_IP_LIMITED.getValue()) ) {
                cause += "and el.lastVisitIP=:ip ";
                params.put("ip", ipAddress);
            }

            Integer passesFromThisIP = idObjectService.checkExist(SurveySession.class, null, cause, params, survey.getMaxAttempts() + 1);

            if (passesFromThisIP >= survey.getMaxAttempts()) {
                throw new MaxAttemptsException();
            }
        }

        // Check max respondents
        if (survey.getMaxRespondents() != null && survey.getMaxRespondents() > 0) {
            Map<String, Object> params = new HashMap<>();
            String cause = "el.survey.id=:surveyId ";
            params.put("surveyId", survey.getId());

            Integer totalPasses = idObjectService.checkExist(SurveySession.class, null, cause,
                    params, survey.getMaxRespondents() + 1);
            if (totalPasses >= survey.getMaxRespondents()) {
                throw new RespondentLimitException();
            }
        }

        SurveySession surveySession = new SurveySession();
        surveySession.setStarted(now);
        surveySession.setLastVisitIP(ipAddress);
        if (user != null) {
            surveySession.setUser(idObjectService.getObjectById(User.class, user.getId()));
        }

        if (survey.getTimeLimit() != null && survey.getTimeLimit() > 0) {
            surveySession.setExpirationDate(new Date(now.getTime() + survey.getTimeLimit()));
        }

        Integer[] pageVisitHistory = new Integer[1];
        pageVisitHistory[0] = 0;
        surveySession.setPageVisitHistory(pageVisitHistory);
        surveySession.setLink(survey.getLink());
        surveySession.setConclusion(survey.getConclusion());
        surveySession.setSurvey(survey);
        surveySession.setPreviewSession(false);
        idObjectService.save(surveySession);

        SurveyInteractionDTO surveyInteractionDTO = new SurveyInteractionDTO();
        surveyInteractionDTO.setSurveySessionId(surveySession.getId());
        surveyInteractionDTO.setSurveyPage(getSurveyPage(surveySession, 0));
        return surveyInteractionDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyInteractionDTO startSurvey(UUID surveyId, AuthorizedUser user, String ipAddress)
            throws ObjectNotFoundException, RespondentLimitException, ForbiddenException, MaxAttemptsException {
        return startSurvey(idObjectService.getObjectById(Survey.class, surveyId), user, ipAddress);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyInteractionDTO startSurveyByExternalId(String externalId, AuthorizedUser user, String ipAddress)
            throws ObjectNotFoundException, RespondentLimitException, ForbiddenException, MaxAttemptsException {
        Map<String, Object> params = new HashMap<>();
        params.put("externalId", externalId);

        List<Survey> surveys = idObjectService.getList(Survey.class, null, "el.externalId = :externalId",
                params, null, null, null);

        if (surveys.isEmpty()) {
            throw new ObjectNotFoundException();
        }

        return startSurvey(surveys.iterator().next(), user, ipAddress);
    }

    private SurveyPageDTO getSurveyPage(SurveySession surveySession, int pageIndex) throws ObjectNotFoundException {
        Map<String, Object> params = new HashMap<>();
        String cause = "el.survey.id = :surveyId and el.pageIndex = :pageIndex ";
        params.put("surveyId", surveySession.getSurvey().getId());
        params.put("pageIndex", pageIndex);

        List<SurveyPage> surveyPages = idObjectService.getList(SurveyPage.class, null, cause, params, null, null, null, 1);
        if (surveyPages.isEmpty()) {
            throw new ObjectNotFoundException();
        }

        SurveyPage surveyPage = surveyPages.iterator().next();
        SurveyPageDTO dto = SurveyPageDTO.prepare(surveyPage);

        // 1. Getting list of questions of the current page
        params.clear();
        cause = "el.surveyPage.id=:surveyPageId ";
        params.put("surveyPageId", surveyPage.getId());

        List<SurveyQuestion> questions = idObjectService.getList(SurveyQuestion.class, "left join fetch el.catalog",
                cause, params, "el.questionIndex", "ASC", null, null);

        // 2. Getting the logic. For the web, select only HIDE_QUESTION / SHOW_QUESTION
        params.clear();
        cause = "el.surveyPage.id = :surveyPageId AND el.surveyLogicActionType.id in (:logicActionTypeIds) ";
        Set<UUID> logicActionTypeIds = new HashSet<>();
        logicActionTypeIds.add(DataConstants.LogicActionTypes.HIDE_QUESTION.getValue());
        logicActionTypeIds.add(DataConstants.LogicActionTypes.SHOW_QUESTION.getValue());
        params.put("surveyPageId", surveyPage.getId());
        params.put("logicActionTypeIds", logicActionTypeIds);
        List<SurveyLogicTrigger> logicTriggers = idObjectService.getList(SurveyLogicTrigger.class,
                null, cause, params, null, null, null);

        HashMap<SurveyAnswerVariant, List<SurveyLogicTriggerDTO>> triggersForAnswers = new HashMap<>();
        for (SurveyLogicTrigger trigger : logicTriggers) {
            if (!triggersForAnswers.containsKey(trigger.getAnswerVariant())) {
                List<SurveyLogicTriggerDTO> list = new LinkedList<>();
                list.add(SurveyLogicTriggerDTO.prepare(trigger));
                triggersForAnswers.put(trigger.getAnswerVariant(), list);
                continue;
            }

            triggersForAnswers.get(trigger.getAnswerVariant()).add(SurveyLogicTriggerDTO.prepare(trigger));
        }

        Set<UUID> questionIds = new HashSet<>();
        for (SurveyQuestion question : questions) {
            questionIds.add(question.getId());
        }

        if (!questionIds.isEmpty()) {
            // 3. Getting answer variants
            params.clear();
            cause = "el.surveyQuestion.id in (:questionIds) ";
            params.put("questionIds", questionIds);

            HashMap<UUID, List<SurveyAnswerVariant>> answerVariants = asListAnswerVariantHashMap(idObjectService.getList(SurveyAnswerVariant.class, null,
                    cause, params, "el.sortOrder", "ASC", null, null));

            List<SurveyQuestionDTO> surveyQuestionDTOs = new LinkedList<>();
            for (SurveyQuestion question : questions) {
                List<SurveyAnswerVariantDTO> answerVariantsDTO = null;
                List<SurveyAnswerVariant> answersList = answerVariants.get(question.getId());
                if (answersList != null) {
                    answerVariantsDTO = new LinkedList<>();
                    for (SurveyAnswerVariant answerVariant : answersList) {
                        SurveyAnswerVariantDTO answerVariantDTO = SurveyAnswerVariantDTO.prepare(answerVariant);
                        // add web logic triggers if exists
                        if (triggersForAnswers.containsKey(answerVariant)) {
                            answerVariantDTO.setWebLogicTriggers(triggersForAnswers.get(answerVariant));
                        }
                        answerVariantsDTO.add(answerVariantDTO);
                    }
                }

                SurveyQuestionDTO surveyQuestionDTO = SurveyQuestionDTO.prepare(question);
                surveyQuestionDTO.setAnswerVariants(answerVariantsDTO);
                surveyQuestionDTOs.add(surveyQuestionDTO);
            }
            dto.setQuestions(surveyQuestionDTOs);
        }

        return dto;
    }

    /**
     * Saves or updates entire survey. Deletes specified pages, questions, answers and logic triggers.
     * @param surveyDTO dto
     * @return Created/updated survey id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Survey saveEntireSurvey(SurveyDTO surveyDTO, AuthorizedUser user)
            throws ObjectNotFoundException, LogicDependencyException, ResultDependencyException, BadDTOException {

        Survey survey = saveSurvey(surveyDTO, user);
        Map<String, Object> params = new HashMap<>();

        // Do not change operation order
        // 1. Delete specified survey pages
        if (surveyDTO.getPagesToDelete() != null && surveyDTO.getPagesToDelete().size() > 0) {
            params.put("ids", surveyDTO.getPagesToDelete());
            List<SurveyPage> pages = idObjectService.getList(SurveyPage.class, null, "el.id in (:ids)", params,
                    null, null, null, null);
            for (SurveyPage page : pages)
                deleteSurveyPage(page.getId(), false);
            params.clear();
        }

        if (surveyDTO.getPages() != null) {
            for (SurveyPageDTO surveyPageDTO : surveyDTO.getPages()) {

                // 2. Delete specified survey logic triggers
                if (surveyPageDTO.getLogicTriggersToDelete() != null && surveyPageDTO.getLogicTriggersToDelete().size() > 0) {
                    params.put("ids", surveyPageDTO.getLogicTriggersToDelete());
                    List<SurveyLogicTrigger> logicTriggers = idObjectService.getList(SurveyLogicTrigger.class, null, "el.id in (:ids)", params,
                            null, null, null, null);
                    for (SurveyLogicTrigger trigger : logicTriggers)
                        deleteSurveyLogicTrigger(trigger.getId());

                    params.clear();
                }

                // 3. Delete specified survey questions
                if (surveyPageDTO.getQuestionsToDelete() != null && surveyPageDTO.getQuestionsToDelete().size() > 0) {
                    params.put("ids", surveyPageDTO.getQuestionsToDelete());
                    List<SurveyQuestion> questions = idObjectService.getList(SurveyQuestion.class, null, "el.id in (:ids)", params,
                            null, null, null, null);
                    for (SurveyQuestion question : questions)
                        deleteSurveyQuestion(question.getId(), true, false);

                    params.clear();
                }

                for (SurveyQuestionDTO questionDTO : surveyPageDTO.getQuestions()) {
                    // 4. Delete specified survey answer variants
                    if (questionDTO.getAnswersToDelete() != null && questionDTO.getAnswersToDelete().size() > 0) {
                        params.put("ids", questionDTO.getAnswersToDelete());
                        List<SurveyAnswerVariant> answers = idObjectService.getList(SurveyAnswerVariant.class, null, "el.id in (:ids)", params,
                                null, null, null, null);
                        for (SurveyAnswerVariant a : answers)
                            deleteSurveyAnswerVariant(a.getId(), true, false);

                        params.clear();
                    }
                }
            }

            HashMap<SurveyLogicTriggerDTO, SurveyLogicTrigger> targetQuestionTriggers = new HashMap<>();

            for (SurveyPageDTO surveyPageDTO : surveyDTO.getPages()) {
                surveyPageDTO.setSurveyId(survey.getId());
                SurveyPage surveyPage = saveSurveyPage(surveyPageDTO);

                // page layer: here we can update existing logic triggers or add new page logic trigger
                for (SurveyLogicTriggerDTO logicTriggerDTO : surveyPageDTO.getLogicTriggers()) {
                    logicTriggerDTO.setSurveyPageId(surveyPage.getId());

                    if (logicTriggerDTO.getId() == null && (logicTriggerDTO.getAnswerVariantId() != null ||
                            logicTriggerDTO.getSurveyQuestionId() != null ||
                            logicTriggerDTO.getTargetQuestionId() != null)) {
                        throw new BadDTOException("Logic trigger on page " + surveyPage.getPageIndex() + " contains incompatible fields. " +
                                "If you're trying to create new logic trigger for question or answer variant, put this model to corresponding DTO.");
                    }

                    SurveyLogicTrigger trigger = saveSurveyLogicTrigger(logicTriggerDTO);

                    // if there is target question pointer and no target question specified
                    if (logicTriggerDTO.getTargetQuestionIndex() != null)
                        targetQuestionTriggers.put(logicTriggerDTO, trigger);
                }

                HashSet<Integer> questionIndexes = new HashSet<>();
                for (SurveyQuestionDTO surveyQuestionDTO : surveyPageDTO.getQuestions()) {
                    surveyQuestionDTO.setSurveyPageId(surveyPage.getId());
                    SurveyQuestion surveyQuestion = saveSurveyQuestion(surveyQuestionDTO);

                    // make sure there is no duplicate indexes
                    if (questionIndexes.contains(surveyQuestion.getQuestionIndex()))
                        throw new BadDTOException(messageSource.getMessage("survey.BAD_DTO_DUPLICATED_QUESTION_INDEXES",
                                null, LocaleHolder.getLocale()) + " " + surveyQuestion.getQuestionIndex());
                    questionIndexes.add(surveyQuestion.getQuestionIndex());

                    // question layer: NEW QUESTION LOGIC TRIGGERS ONLY
                    for (SurveyLogicTriggerDTO logicTriggerDTO : surveyQuestionDTO.getLogicTriggersToAdd()) {
                        logicTriggerDTO.setSurveyQuestionId(surveyQuestion.getId());
                        logicTriggerDTO.setSurveyPageId(surveyPage.getId());

                        if (logicTriggerDTO.getId() != null) {
                            throw new BadDTOException("Logic trigger for question " + surveyQuestion.getText() + " already contains id. " +
                                    "If you're trying to update logic trigger, put this model to SurveyPageDTO.logicTriggers.");
                        }
                        if (logicTriggerDTO.getAnswerVariantId() != null) {
                            throw new BadDTOException("Logic trigger for question " + surveyQuestion.getText() + " contains incompatible fields." +
                                    "If you're trying to create new logic trigger for answer variant, put this model to corresponding DTO.");
                        }

                        SurveyLogicTrigger trigger = saveSurveyLogicTrigger(logicTriggerDTO);
                        if (logicTriggerDTO.getTargetQuestionIndex() != null)
                            targetQuestionTriggers.put(logicTriggerDTO, trigger);
                    }

                    for (SurveyAnswerVariantDTO answerVariantDTO : surveyQuestionDTO.getAnswerVariants()) {
                        answerVariantDTO.setSurveyQuestionId(surveyQuestion.getId());
                        SurveyAnswerVariant variant = saveSurveyAnswerVariant(answerVariantDTO);

                        // answer layer: NEW ANSWER VARIANT LOGIC TRIGGERS ONLY
                        for (SurveyLogicTriggerDTO logicTriggerDTO : answerVariantDTO.getLogicTriggersToAdd()) {
                            logicTriggerDTO.setSurveyQuestionId(surveyQuestion.getId());
                            logicTriggerDTO.setSurveyPageId(surveyPage.getId());
                            logicTriggerDTO.setAnswerVariantId(variant.getId());

                            if (logicTriggerDTO.getId() != null) {
                                throw new BadDTOException("Logic trigger for answer variant " + variant.getText() + " already contains id. " +
                                        "If you're trying to update logic trigger, put this model to SurveyPageDTO.logicTriggers.");
                            }

                            SurveyLogicTrigger trigger = saveSurveyLogicTrigger(logicTriggerDTO);
                            if (logicTriggerDTO.getTargetQuestionIndex() != null)
                                targetQuestionTriggers.put(logicTriggerDTO, trigger);

                        }
                    }
                }
            }

            // if have some target questions in logic triggers
            if (!targetQuestionTriggers.isEmpty()) {
                // check all created logic triggers on target question existence case
                for (Map.Entry<SurveyLogicTriggerDTO, SurveyLogicTrigger> entry : targetQuestionTriggers.entrySet()) {
                    SurveyLogicTriggerDTO dto = entry.getKey();
                    SurveyLogicTrigger created = entry.getValue();

                    params.clear();
                    params.put("questionIndex", dto.getTargetQuestionIndex());
                    params.put("pageId", created.getSurveyPage().getId());
                    List<SurveyQuestion> possibleSurveyQuestions = idObjectService.getList(SurveyQuestion.class, null,
                            "el.questionIndex = :questionIndex and el.surveyPage.id = :pageId",
                            params, null, null, null, 1);
                    if (possibleSurveyQuestions.size() == 0) throw new BadDTOException("Logic trigger: no such question for specified index " + dto.getTargetQuestionIndex());

                    created.setTargetQuestion(possibleSurveyQuestions.get(0));
                }
            }
        }
        return survey;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyInteractionDTO goToPage(UUID surveySessionId, int pageIndex)
            throws ObjectNotFoundException, ForbiddenException {
        SurveySession surveySession = idObjectService.getObjectById(SurveySession.class, surveySessionId);

        if (surveySession == null) {
            throw new ObjectNotFoundException();
        }
        if (surveySession.getEnded() != null) {
            throw new ForbiddenException();
        }
        if (surveySession.getExpirationDate() != null && surveySession.getExpirationDate().before(new Date())) {
            throw new ForbiddenException();
        }

        Integer[] pagesHistory = Arrays.copyOf(surveySession.getPageVisitHistory(),
                surveySession.getPageVisitHistory().length + 1);
        pagesHistory[pagesHistory.length-1] = pageIndex;

        surveySession.setPageVisitHistory(pagesHistory);
        idObjectService.save(surveySession);

        SurveyInteractionDTO surveyInteractionDTO = new SurveyInteractionDTO();
        surveyInteractionDTO.setSurveySessionId(surveySession.getId());
        surveyInteractionDTO.setSurveyPage(getSurveyPage(surveySession, pageIndex));

        return surveyInteractionDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    public SurveyInteractionDTO goBack(UUID surveySessionId) throws ObjectNotFoundException, ForbiddenException {
        SurveySession surveySession = idObjectService.getObjectById(SurveySession.class, surveySessionId);
        if (surveySession == null) {
            throw new ObjectNotFoundException();
        }

        Survey survey = idObjectService.getObjectById(Survey.class, surveySession.getSurvey().getId());
        if (survey == null) {
            throw new ObjectNotFoundException();
        }

        if (!survey.isReturnAllowed()) {
            throw new ForbiddenException();
        }

        if (surveySession.getEnded() != null || // session already ended
                (surveySession.getExpirationDate() != null && surveySession.getExpirationDate().before(new Date())) || // hit time limit
                (surveySession.getPageVisitHistory() == null || surveySession.getPageVisitHistory().length <= 1)) { // trying to go back to nothing
            throw new ForbiddenException();
        }

        Integer[] visitHistory = Arrays.copyOf(surveySession.getPageVisitHistory(), surveySession.getPageVisitHistory().length-1);
        surveySession.setPageVisitHistory(visitHistory);

        idObjectService.save(surveySession);

        int previousPageIndex = visitHistory[visitHistory.length-1];

        // question id, List<answerDTO>
        HashMap<UUID, List<AnswerDTO>> pageAnswers = new HashMap<>();

        Map<String, Object> params = new HashMap<>();
        params.put("pageIndex", previousPageIndex);
        params.put("surveySessionId", surveySession.getId());

        List<SurveyQuestionAnswer> answersList = idObjectService.getList(SurveyQuestionAnswer.class, "left join el.surveyPage sp",
                "el.surveySession.id = :surveySessionId AND sp.pageIndex=:pageIndex",
                params, null, null, null);

        for (SurveyQuestionAnswer model : answersList) {
            List<AnswerDTO> answerDTOs = pageAnswers.get(model.getSurveyQuestion().getId());
            if (answerDTOs == null) {
                answerDTOs = new ArrayList<>();
                pageAnswers.put(model.getSurveyQuestion().getId(), answerDTOs);
            }
            answerDTOs.add(AnswerDTO.fromModel(model));
        }

        SurveyInteractionDTO surveyInteractionDTO = new SurveyInteractionDTO();
        surveyInteractionDTO.setSurveySessionId(surveySession.getId());
        surveyInteractionDTO.setSurveyPage(getSurveyPage(surveySession, previousPageIndex));
        surveyInteractionDTO.setPageAnswers(pageAnswers);
        return surveyInteractionDTO;
    }

    @Override
    public SurveyInteractionDTO continueSurvey(UUID surveySessionId) throws ObjectNotFoundException, ForbiddenException {
        SurveySession surveySession = idObjectService.getObjectById(SurveySession.class, surveySessionId);
        if (surveySession == null) {
            throw new ObjectNotFoundException();
        }
        if (surveySession.getEnded() != null) {
            throw new ForbiddenException();
        }
        if (surveySession.getExpirationDate() != null && surveySession.getExpirationDate().before(new Date())) {
            throw new ForbiddenException();
        }

        SurveyInteractionDTO surveyInteractionDTO = new SurveyInteractionDTO();
        surveyInteractionDTO.setSurveySessionId(surveySession.getId());
        surveyInteractionDTO.setSurveyPage(getSurveyPage(surveySession,
                surveySession.getPageVisitHistory()[surveySession.getPageVisitHistory().length-1]));

        return surveyInteractionDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyInteractionDTO saveAnswersAndContinue(UUID surveySessionId, PageAnswersDTO dto)
            throws ObjectNotFoundException, ForbiddenException, UnansweredException, UnansweredOtherOptionException {

        SurveySession surveySession = idObjectService.getObjectById(SurveySession.class, surveySessionId);
        final Date dateNow = new Date();

        if (surveySession == null) {
            throw new ObjectNotFoundException();
        }
        if (surveySession.getEnded() != null) {
            throw new ForbiddenException();
        }

        // if user passing time limited survey
        if (surveySession.getExpirationDate() != null && surveySession.getExpirationDate().before(dateNow)) {
            throw new ForbiddenException();
        }

        Survey survey = idObjectService.getObjectById(Survey.class, surveySession.getSurvey().getId());

        boolean finishSurvey = false;

        int lastVisitedPageIndex = surveySession.getPageVisitHistory()[surveySession.getPageVisitHistory().length-1];

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", surveySession.getId());
        params.put("pageIndex", lastVisitedPageIndex);
        List<SurveyQuestionAnswer> possibleAnswers = idObjectService.getList(SurveyQuestionAnswer.class, "left join el.surveyPage sp",
                "el.surveySession.id = :sessionId AND sp.pageIndex = :pageIndex",
                params, null, null, null);

        for (SurveyQuestionAnswer questionAnswer : possibleAnswers) {
            idObjectService.delete(SurveyQuestionAnswer.class, questionAnswer.getId());
        }

        int nextPage = lastVisitedPageIndex + 1;

        // 1. Getting the list of questions on the last visited page
        params.clear();
        params.put("lastVisitedPageIndex", lastVisitedPageIndex);
        params.put("surveyId", surveySession.getSurvey().getId());
        HashMap<UUID, SurveyQuestion> surveyQuestionsHashMap = asUUIDHashMap(idObjectService.getList(SurveyQuestion.class, "left join el.surveyPage sp left join sp.survey sv left join el.catalog cat",
                "sv.id=:surveyId and sp.pageIndex=:lastVisitedPageIndex",
                params, "el.questionIndex", "ASC", null, null));

        // 2. Getting all answer variants
        // Stored by survey answer id
        HashMap<UUID, SurveyAnswerVariant> surveyAnswersHashMap = new HashMap<>();
        HashMap<UUID, List<SurveyAnswerVariant>> answerVariantsByQuestion = new HashMap<>();
        if (dto.containsNonTextAnswers()) {
            params.clear();
            params.put("questionIds", surveyQuestionsHashMap.keySet());
            List<SurveyAnswerVariant> list = idObjectService.getList(SurveyAnswerVariant.class, null,
                    "el.surveyQuestion.id in (:questionIds)", params, null, null, null);
            surveyAnswersHashMap = asUUIDHashMap(list);
            answerVariantsByQuestion = asListAnswerVariantHashMap(list);
        }

        // 3. Getting logic by last visited page
        params.clear();
        params.put("lastVisitedPageIndex", lastVisitedPageIndex);
        params.put("surveyId", surveySession.getSurvey().getId());
        // TODO: sort logic by 1. PAGE -> 2. QUESTION INDEX -> 3. ANSWER INDEX
        List<SurveyLogicTrigger> logicTriggers =
                idObjectService.getList(SurveyLogicTrigger.class, "left join el.surveyPage sp left join sp.survey sv",
                        "sv.id=:surveyId and  sp.pageIndex=:lastVisitedPageIndex",
                        params, null, null, null);

        Set<UUID> completelyAnsweredQuestions = new HashSet<>();
        Set<UUID> selectedAnswers = new HashSet<>();

        HashMap<UUID, List<SurveyQuestionAnswer>> questionAnswers = new HashMap<>();
        Set<UUID> matrixQuestionIds = new HashSet<>();

        // save received answers
        for (Map.Entry<UUID, List<AnswerDTO>> entry : dto.getAnswers().entrySet()) {
            for (AnswerDTO answerDTO : entry.getValue()) {
                SurveyQuestion question = surveyQuestionsHashMap.get(entry.getKey());
                SurveyAnswerVariant answerVariant = null;

                // if question is using external catalog then we have no answer variants in our database
                // it means we can allow answers with no answer variant specified (i.e only with text field)
                boolean usingExternalCatalog = question.getCatalog() != null && question.getCatalog().isExternal();

                boolean isAnswerVariantSpecifyRequired =
                        !usingExternalCatalog && (question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.LISTBOX.getValue()) ||
                        question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.CHECKBOX.getValue()) ||
                        question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.RADIOBUTTON.getValue()) ||
                        question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.COMBOBOX.getValue()));

                if (answerDTO.getAnswerVariantId() != null)
                    answerVariant = surveyAnswersHashMap.get(answerDTO.getAnswerVariantId());
                else {
                    if (isAnswerVariantSpecifyRequired) throw new ForbiddenException("Answer variant not specified");
                }

                if (answerVariant != null && answerVariant.isCustomVariant()) {
                    if (survey.getClarifyCustomAnswer() && StringUtils.isBlank(answerDTO.getText())) {
                        throw new UnansweredOtherOptionException("Custom text field of question is required", question.getText());
                    }
                }

                boolean isTextFieldRequired = question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.TEXT_MULTILINE.getValue()) ||
                        question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.TEXT_SINGLE_LINE.getValue()) ||
                        question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.RATING_SCALE.getValue());

                if (question.getRequired() && isTextFieldRequired && StringUtils.isBlank(answerDTO.getText())) {
                    throw new UnansweredException("Text field of question is required", question.getText());
                }
                boolean isMatrixQuestion = question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.MATRIX_CHECKBOX.getValue()) ||
                        question.getSurveyQuestionType().getId().equals(DataConstants.QuestionTypes.MATRIX_RADIOBUTTON.getValue());

                if (isMatrixQuestion && (answerDTO.getSelectedMatrixColumn() == null || answerDTO.getSelectedMatrixRow() == null)) {
                    throw new ForbiddenException("You're answering to matrix question without specified row index or column index");
                }

                // check out of bounds
                if (isMatrixQuestion) {
                    int maxRows = question.getMatrixRows().length;
                    boolean hasCustomVariant = false;
                    if (answerVariantsByQuestion.get(question.getId()) != null) { // if matrix has custom variant
                        maxRows++;
                        hasCustomVariant = true;
                    }

                    boolean rowIndexCheck = answerDTO.getSelectedMatrixRow() >= 0 && answerDTO.getSelectedMatrixRow() < maxRows;
                    boolean columnIndexCheck = answerDTO.getSelectedMatrixColumn() >= 0 && answerDTO.getSelectedMatrixColumn() < question.getMatrixColumns().length;

                    if (!rowIndexCheck) {
                        throw new ForbiddenException("You're answering to matrix row that don't exist. Expected 0-" + (maxRows-1)
                                + ", but received " + answerDTO.getSelectedMatrixRow());
                    }

                    if (!columnIndexCheck) {
                        throw new ForbiddenException("You're answering to matrix row that don't exist. Expected 0-" +
                                (question.getMatrixColumns().length-1) + ", but received " + answerDTO.getSelectedMatrixColumn());
                    }

                    // custom matrix variant is always last row now. If it not specified then this is non-valid answer
                    boolean answeringToCustomVariant = hasCustomVariant && answerDTO.getSelectedMatrixRow() == maxRows-1;
                    if (answeringToCustomVariant && answerVariant == null)
                        throw new ForbiddenException("Custom answer variant id is not specified");
                }

                SurveyQuestionAnswer surveyQuestionAnswer = new SurveyQuestionAnswer(surveySession,
                        question,
                        answerVariant,
                        answerDTO.getText(),
                        null); // TODO: stored file

                surveyQuestionAnswer.setSelectedMatrixRow(answerDTO.getSelectedMatrixRow());
                surveyQuestionAnswer.setSelectedMatrixColumn(answerDTO.getSelectedMatrixColumn());

                List<SurveyQuestionAnswer> list = questionAnswers.get(question.getId());
                if (list == null) {
                    list = new LinkedList<>();
                    questionAnswers.put(question.getId(), list);
                }
                list.add(surveyQuestionAnswer);

                if (!isMatrixQuestion) {
                    completelyAnsweredQuestions.add(question.getId());
                } else if (question.getRequired()) {
                    // matrix question requirements will be checked later
                    matrixQuestionIds.add(question.getId());
                }

                if (answerVariant != null)
                    selectedAnswers.add(answerVariant.getId());
            }
        }

        // check all rows of required matrix question
        for (UUID questionId : matrixQuestionIds) {
            SurveyQuestion question = surveyQuestionsHashMap.get(questionId);
            HashSet<Integer> rowsTotal = new HashSet<>();
            Integer customVariantIndex = null;
            for (int i = 0; i < question.getMatrixRows().length; i++) {
                rowsTotal.add(i);
            }

            if (answerVariantsByQuestion.get(question.getId()) != null) { // if matrix has custom variant
                customVariantIndex = question.getMatrixRows().length;
                rowsTotal.add(customVariantIndex); // add it as last row
            }

            HashSet<Integer> rowIndexesAnswered = new HashSet<>();
            for (SurveyQuestionAnswer answer : questionAnswers.get(questionId)) {
                rowIndexesAnswered.add(answer.getSelectedMatrixRow());
            }

            if (survey.getCanIgnoreCustomAnswer() && customVariantIndex != null) {
                rowsTotal.remove(customVariantIndex);
                rowIndexesAnswered.remove(customVariantIndex);
            }

            if (rowIndexesAnswered.equals(rowsTotal)) {
                completelyAnsweredQuestions.add(questionId);
            }
        }

        for (Map.Entry<UUID, SurveyQuestion> entry : surveyQuestionsHashMap.entrySet()) {
            if (entry.getValue().getRequired() && !completelyAnsweredQuestions.contains(entry.getKey()))
                throw new UnansweredException("Unanswered", entry.getValue().getText());
        }

        for (SurveyLogicTrigger trigger : logicTriggers) {
            LogicTriggerCheckItem checkItem = LogicTriggerCheckItem.PAGE;
            if (trigger.getSurveyQuestion() != null) {
                checkItem = LogicTriggerCheckItem.QUESTION;
                if (trigger.getAnswerVariant() != null) checkItem = LogicTriggerCheckItem.ANSWER;
            }

            boolean triggered = false;
            switch (checkItem) {
                case PAGE:
                    triggered = true;
                    break;
                case QUESTION:
                    boolean answeredTrigger = trigger.isInteractionRequired() && completelyAnsweredQuestions.contains(trigger.getSurveyQuestion().getId());
                    boolean unansweredTrigger = !trigger.isInteractionRequired() && !completelyAnsweredQuestions.contains(trigger.getSurveyQuestion().getId());
                    triggered = answeredTrigger || unansweredTrigger;
                    break;
                case ANSWER:
                    boolean selectedTrigger = trigger.isInteractionRequired() && selectedAnswers.contains(trigger.getAnswerVariant().getId());
                    boolean unselectedTrigger = !trigger.isInteractionRequired() && !selectedAnswers.contains(trigger.getAnswerVariant().getId());
                    triggered = selectedTrigger || unselectedTrigger;
                    break;
            }

            if (triggered) {
                if (trigger.getSurveyLogicActionType().getId().equals(DataConstants.LogicActionTypes.CHANGE_CONCLUSION.getValue())) {
                    surveySession.setConclusion(trigger.getNewConclusion());
                }

                if (trigger.getSurveyLogicActionType().getId().equals(DataConstants.LogicActionTypes.CHANGE_LINK.getValue())) {
                    surveySession.setLink(trigger.getNewLink());
                }

                if (trigger.getSurveyLogicActionType().getId().equals(DataConstants.LogicActionTypes.GO_TO_PAGE.getValue())) {
                    nextPage = trigger.getPageIndex();
                }

                if (!finishSurvey) {
                    finishSurvey = trigger.getSurveyLogicActionType().getId().equals(DataConstants.LogicActionTypes.END_SURVEY.getValue());
                }

                // answers is not needed because question is hidden
                if (trigger.getSurveyLogicActionType().getId().equals(DataConstants.LogicActionTypes.HIDE_QUESTION.getValue())) {
                    questionAnswers.remove(trigger.getTargetQuestion().getId());
                }
            }
        }

        // finally, save answers
        for (List<SurveyQuestionAnswer> answers : questionAnswers.values()) {
            for (SurveyQuestionAnswer answer : answers) {
                idObjectService.save(answer);
            }
        }

        // if next page is not exists, this is finish
        params.clear();
        params.put("surveyId", surveySession.getSurvey().getId());
        params.put("pageIndex", nextPage);
        if (!finishSurvey && idObjectService.checkExist(SurveyPage.class, null, "el.survey.id=:surveyId and el.pageIndex=:pageIndex",
                params, 1) == 0) {
            finishSurvey = true;
        }

        SurveyInteractionDTO surveyInteractionDTO = new SurveyInteractionDTO();
        surveyInteractionDTO.setSurveySessionId(surveySession.getId());
        if (finishSurvey) {
            SurveyConclusionDTO surveyConclusionDTO = new SurveyConclusionDTO();
            surveyConclusionDTO.setConclusion(surveySession.getConclusion());
            surveyConclusionDTO.setLink(surveySession.getLink());
            surveyInteractionDTO.setSurveyConclusion(surveyConclusionDTO);

            surveySession.setEnded(dateNow);
        } else {
            surveyInteractionDTO.setSurveyPage(getSurveyPage(surveySession, nextPage));

            Integer[] pagesHistory = Arrays.copyOf(surveySession.getPageVisitHistory(),
                    surveySession.getPageVisitHistory().length + 1);
            pagesHistory[pagesHistory.length-1] = nextPage;
            surveySession.setPageVisitHistory(pagesHistory);
        }

        if (finishSurvey && surveySession.getPreviewSession()) {
            params.clear();
            params.put("surveySessionId", surveySession.getId());
            idObjectService.delete(SurveyQuestionAnswer.class, "el.surveySession.id = :surveySessionId", params);
            idObjectService.delete(SurveySession.class, surveySession.getId());
        } else {
            idObjectService.save(surveySession);
        }
        return surveyInteractionDTO;
    }

    @Override
    public EntityListResponse<SurveyDTO> getSurveysPaged(String name, Boolean getExpired, boolean calculate, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (!StringUtils.isEmpty(name)) {
            params.put("name", "%%" + StringUtils.lowerCase(name) + "%%");
            cause += "and lower(el.name) like :name ";
        }

        if (getExpired != null && getExpired) {
            params.put("dateNow", new Date());
            cause += "and el.expirationDate < :dateNow ";
        }

        Integer totalCount = calculate ? idObjectService.getCount(Survey.class, null, countFetches, cause, params) : null;

        EntityListResponse<SurveyDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<Survey> items = idObjectService.getList(Survey.class, null, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (Survey e : items) {
            SurveyDTO el = SurveyDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Survey saveSurvey(SurveyDTO dto, AuthorizedUser user) throws ObjectNotFoundException {
        Survey entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(Survey.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new Survey();
        }

        entity.setActive(dto.getActive());
        entity.setName(dto.getName());
        entity.setStartDate(dto.getStartDate());
        entity.setExpirationDate(dto.getExpirationDate());
        entity.setShowProgress(dto.getShowProgress());
        entity.setShowQuestionNumber(dto.getShowQuestionNumber());
        entity.setAllowReturn(dto.getAllowReturn());
        entity.setClarifyCustomAnswer(dto.getClarifyCustomAnswer());
        entity.setIntroduction(dto.getIntroduction());
        entity.setConclusion(dto.getConclusion());
        entity.setLink(dto.getLink());
        entity.setMaxRespondents(dto.getMaxRespondents());
        entity.setTimeLimit(dto.getTimeLimit());
        entity.setMaxAttempts(dto.getMaxAttempts());
        entity.setSurveyParticipationType(ds.get(SurveyParticipationType.class, dto.getParticipationTypeId()));
        entity.setOwner(idObjectService.getObjectById(User.class, user.getId()));
        entity.setExternalId(dto.getExternalId());
        entity.setCanIgnoreCustomAnswer(dto.getCanIgnoreCustomAnswer());

        entity = idObjectService.save(entity);

        if (StringUtils.isEmpty(dto.getExternalId())) {
            entity.setExternalId(entity.getId().toString());
            entity = idObjectService.save(entity);
        }

        return entity;
    }

    private SurveyDTO getSurvey(Survey survey, boolean entire) throws ObjectNotFoundException {
        if (survey == null) {
            throw new ObjectNotFoundException();
        }

        SurveyDTO surveyDTO = SurveyDTO.prepare(survey);
        if (entire) {
            Map<String, Object> params = new HashMap<>();
            params.put("surveyId", survey.getId());
            List<SurveyPage> surveyPages = idObjectService.getList(SurveyPage.class, null, "el.survey.id = :surveyId", params,
                    "el.pageIndex ASC", null, null);

            HashMap<UUID, SurveyPageDTO> pagesDTO = new HashMap<>();
            for (SurveyPage page : surveyPages) {
                pagesDTO.put(page.getId(), SurveyPageDTO.prepare(page));
            }

            if (!pagesDTO.isEmpty()) {
                params.clear();
                params.put("pageIds", pagesDTO.keySet());
                List<SurveyQuestion> surveyQuestions = idObjectService.getList(SurveyQuestion.class, "left join fetch el.catalog", "el.surveyPage.id in (:pageIds)", params,
                        "el.questionIndex ASC", null, null);

                List<SurveyLogicTrigger> logicTriggers = idObjectService.getList(SurveyLogicTrigger.class, null, "el.surveyPage.id in (:pageIds)", params,
                        null, null, null);

                HashMap<UUID, SurveyQuestionDTO> questionsDTO = new HashMap<>();
                for (SurveyQuestion question : surveyQuestions) {
                    SurveyQuestionDTO questionDTO = SurveyQuestionDTO.prepare(question);
                    questionsDTO.put(question.getId(), questionDTO);
                    pagesDTO.get(question.getSurveyPage().getId()).getQuestions().add(questionDTO);
                }

                for (SurveyLogicTrigger trigger : logicTriggers) {
                    SurveyLogicTriggerDTO dto = SurveyLogicTriggerDTO.prepare(trigger);
                    pagesDTO.get(trigger.getSurveyPage().getId()).getLogicTriggers().add(dto);
                }

                if (!questionsDTO.isEmpty()) {
                    params.clear();
                    params.put("questionIds", questionsDTO.keySet());
                    List<SurveyAnswerVariant> surveyAnswerVariant = idObjectService.getList(SurveyAnswerVariant.class, null, "el.surveyQuestion.id in (:questionIds)", params,
                            "el.sortOrder ASC", null, null);

                    for (SurveyAnswerVariant variant : surveyAnswerVariant) {
                        SurveyAnswerVariantDTO variantDTO = SurveyAnswerVariantDTO.prepare(variant);
                        questionsDTO.get(variant.getSurveyQuestion().getId()).getAnswerVariants().add(variantDTO);
                    }
                }

                for (SurveyPageDTO page : pagesDTO.values()) {
                    surveyDTO.getPages().add(page);
                }

                Collections.sort(surveyDTO.getPages(), new Comparator<SurveyPageDTO>() {
                    @Override
                    public int compare(SurveyPageDTO o1, SurveyPageDTO o2) {
                        return o1.getPageIndex().compareTo(o2.getPageIndex());
                    }
                });

            }
        }
        return surveyDTO;
    }

    @Override
    public SurveyDTO getSurvey(UUID surveyId, boolean entire) throws ObjectNotFoundException {
        return getSurvey(idObjectService.getObjectById(Survey.class, surveyId), entire);
    }

    @Override
    public SurveyDTO getSurveyByExternalId(String externalId, boolean entire) throws ObjectNotFoundException {
        Map<String, Object> params = new HashMap<>();
        params.put("externalId", externalId);

        List<Survey> surveys = idObjectService.getList(Survey.class, null, "el.externalId = :externalId",
                params, null, null, null);

        if (surveys.isEmpty()) {
            throw new ObjectNotFoundException();
        }

        return getSurvey(surveys.iterator().next(), entire);
    }

    @Override
    public EntityListResponse<SurveyAnswerVariantDTO> getSurveyAnswerVariantsPaged(UUID surveyQuestionId, String description, boolean calculate, Integer count, Integer page,
                                                                                   Integer start, String sortField, String sortDir) {
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (surveyQuestionId != null) {
            cause += "and el.surveyQuestion.id=:surveyQuestionId ";
            params.put("surveyQuestionId", surveyQuestionId);
        }
        if (!StringUtils.isEmpty(description)) {
            params.put("description", "%%" + StringUtils.lowerCase(description) + "%%");
            cause += "and lower(el.description) like :description ";
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveyAnswerVariant.class, null, null, cause, params) : null;

        EntityListResponse<SurveyAnswerVariantDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveyAnswerVariant> items = idObjectService.getList(SurveyAnswerVariant.class, null, cause,
                params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (SurveyAnswerVariant e : items) {
            SurveyAnswerVariantDTO el = SurveyAnswerVariantDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyAnswerVariant saveSurveyAnswerVariant(SurveyAnswerVariantDTO dto) throws ObjectNotFoundException {
        SurveyAnswerVariant entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveyAnswerVariant.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveyAnswerVariant();
        }

        entity.setCustomVariant(dto.getCustomVariant());
        entity.setDefaultVariant(dto.getDefaultVariant());
        entity.setSortOrder(dto.getSortOrder());
        entity.setSurveyQuestion(idObjectService.getObjectById(SurveyQuestion.class, dto.getSurveyQuestionId()));
        entity.setText(dto.getText());
        entity.setWeight(dto.getWeight());

        return idObjectService.save(entity);
    }


    @Override
    public SurveyAnswerVariantDTO getSurveyAnswerVariant(UUID id) throws ObjectNotFoundException {
        SurveyAnswerVariant entity = idObjectService.getObjectById(SurveyAnswerVariant.class, id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveyAnswerVariantDTO.prepare(entity);
    }

    @Override
    public EntityListResponse<SurveyPageDTO> getSurveyPagesPaged(UUID surveyId, String description, boolean calculate, Integer count, Integer page,
                                                                 Integer start, String sortField, String sortDir) {
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (surveyId != null) {
            cause += "and el.survey.id=:surveyId ";
            params.put("surveyId", surveyId);
        }

        if (!StringUtils.isEmpty(description)) {
            params.put("description", "%%" + StringUtils.lowerCase(description) + "%%");
            cause += "and lower(el.description) like :description ";
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveyPage.class, null, countFetches, cause, params) : null;

        EntityListResponse<SurveyPageDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveyPage> items = idObjectService.getList(SurveyPage.class, null, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (SurveyPage e : items) {
            SurveyPageDTO el = SurveyPageDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyPage saveSurveyPage(SurveyPageDTO dto) throws ObjectNotFoundException {
        SurveyPage entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveyPage.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveyPage();
        }

        entity.setDescription(dto.getDescription());
        entity.setPageIndex(dto.getPageIndex());
        entity.setSurvey(idObjectService.getObjectById(Survey.class, dto.getSurveyId()));
        return idObjectService.save(entity);
    }


    @Override
    public SurveyPageDTO getSurveyPage(UUID surveyPageId) throws ObjectNotFoundException {
        SurveyPage entity = idObjectService.getObjectById(SurveyPage.class, surveyPageId);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveyPageDTO.prepare(entity);
    }

    @Override
    public EntityListResponse<SurveyQuestionDTO> getSurveyQuestionsPaged(UUID surveyId, UUID surveyPageId,
                                                                         Set<UUID> questionTypes, String text,
                                                                         boolean withVariants, boolean calculate, Integer count, Integer page,
                                                                         Integer start, String sortField, String sortDir) {
        String countFetches = "left join el.surveyPage sp ";
        String fetches = "left join el.surveyPage sp left join fetch el.catalog";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (surveyId != null) {
            cause += "and sp.survey.id=:surveyId ";
            params.put("surveyId", surveyId);
        }

        if (surveyPageId != null) {
            cause += "and el.surveyPage.id=:surveyPageId ";
            params.put("surveyPageId", surveyPageId);
        }

        if (!StringUtils.isEmpty(text)) {
            params.put("text", "%%" + StringUtils.lowerCase(text) + "%%");
            cause += "and lower(el.text) like :text ";
        }

        if (questionTypes != null && questionTypes.size() > 0) {
            params.put("questionTypes", questionTypes);
            cause += "and el.surveyQuestionType.id in (:questionTypes) ";
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveyQuestion.class, null, countFetches, cause, params) : null;

        EntityListResponse<SurveyQuestionDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveyQuestion> items = idObjectService.getList(SurveyQuestion.class, fetches, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);
        Set<UUID> questionIds = new HashSet<>();
        List<SurveyAnswerVariant> variants = Collections.emptyList();
        if (withVariants) {
            for (SurveyQuestion surveyQuestion : items) {
                questionIds.add(surveyQuestion.getId());
            }
            if (!questionIds.isEmpty()) {
                Map<String, Object> pms = new HashMap<>();
                pms.put("questionIds", questionIds);
                variants = idObjectService.getList(SurveyAnswerVariant.class, null, "el.surveyQuestion.id in (:questionIds)", pms, "el.sortOrder ASC", null, null);
            }
        }

        for (SurveyQuestion e : items) {
            SurveyQuestionDTO el = SurveyQuestionDTO.prepare(e);
            entityListResponse.addData(el);
            if (withVariants) {
                for (SurveyAnswerVariant v : variants) {
                    if (v.getSurveyQuestion().getId().equals(e.getId())) {
                        SurveyAnswerVariantDTO dto = SurveyAnswerVariantDTO.prepare(v);
                        el.getAnswerVariants().add(dto);
                    }
                }
            }
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyQuestion saveSurveyQuestion(SurveyQuestionDTO dto) throws ObjectNotFoundException, BadDTOException {
        SurveyQuestion entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveyQuestion.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveyQuestion();
        }

        if (dto.getSurveyQuestionTypeId() == null) throw new BadDTOException("No question type specified");

        SurveyQuestionType surveyQuestionType = ds.get(SurveyQuestionType.class, dto.getSurveyQuestionTypeId());
        if (surveyQuestionType.getId().equals(DataConstants.QuestionTypes.RATING_SCALE.getValue())) {

            if (dto.getScaleMinValue() == null) throw new BadDTOException("Question \"" + dto.getText() + "\" has no scale min value");
            if (dto.getScaleMaxValue() == null) throw new BadDTOException("Question \"" + dto.getText() + "\" has no scale max value");

            if (dto.getScaleStepValue() == null) dto.setScaleStepValue(1);
            if (dto.getScaleMinValueLabel() == null) dto.setScaleMinValueLabel(dto.getScaleMinValue().toString());
            if (dto.getScaleMaxValueLabel() == null) dto.setScaleMaxValueLabel(dto.getScaleMaxValue().toString());
        }

        if (surveyQuestionType.getId().equals(DataConstants.QuestionTypes.MATRIX_CHECKBOX.getValue()) ||
            surveyQuestionType.getId().equals(DataConstants.QuestionTypes.MATRIX_RADIOBUTTON.getValue())) {
            if (dto.getMatrixColumns() == null || dto.getMatrixColumns().length == 0)
                throw new BadDTOException("Question \"" + dto.getText() + "\" requires at least one column");

            if (dto.getMatrixRows() == null || dto.getMatrixRows().length == 0)
                throw new BadDTOException("Question \"" + dto.getText() + "\" requires at least one row");
        }

        SurveyPage surveyPage = idObjectService.getObjectById(SurveyPage.class, dto.getSurveyPageId());
        if (surveyPage == null) {
            throw new BadDTOException("Survey page for question \"" + dto.getText() + "\" is not found");
        }

        entity.setCatalog(dto.getCatalogId() != null ?
                idObjectService.getObjectById(SurveyAnswerVariantCatalog.class, dto.getCatalogId()) : null);

        entity.setScaleStepValue(dto.getScaleStepValue());
        entity.setScaleMaxValueLabel(dto.getScaleMaxValueLabel());
        entity.setScaleMinValueLabel(dto.getScaleMinValueLabel());
        entity.setMatrixColumns(dto.getMatrixColumns());
        entity.setMatrixRows(dto.getMatrixRows());
        entity.setQuestionIndex(dto.getQuestionIndex());
        entity.setHidden(dto.getHidden());
        entity.setRequired(dto.getRequired());
        entity.setSurveyPage(surveyPage);
        entity.setText(dto.getText());
        entity.setSurveyQuestionType(surveyQuestionType);
        entity.setScaleMinValue(dto.getScaleMinValue());
        entity.setScaleMaxValue(dto.getScaleMaxValue());
        entity.setAttachmentExtensions(dto.getAttachmentExtensions());
        entity.setDescription(dto.getDescription());
        return idObjectService.save(entity);
    }


    @Override
    public SurveyQuestionDTO getSurveyQuestion(UUID surveyQuestionId) throws ObjectNotFoundException {
        SurveyQuestion entity = idObjectService.getObjectById(SurveyQuestion.class, "left join fetch el.catalog", surveyQuestionId);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveyQuestionDTO.prepare(entity);
    }

    @Override
    public EntityListResponse<SurveyLogicTriggerDTO> getSurveyLogicTriggersPaged(UUID surveyQuestionId, UUID surveyPageId, UUID surveyAnswerVariantId, boolean calculate, Integer count, Integer page,
                                                                                 Integer start, String sortField, String sortDir) {
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (surveyPageId != null) {
            cause += "and el.surveyPage.id=:surveyPageId ";
            params.put("surveyPageId", surveyPageId);
        }
        if (surveyQuestionId != null) {
            cause += "and el.surveyQuestion.id=:surveyQuestionId ";
            params.put("surveyQuestionId", surveyQuestionId);
        }
        if (surveyQuestionId != null) {
            cause += "and el.surveyAnswerVariant.id=:surveyAnswerVariantId ";
            params.put("surveyAnswerVariantId", surveyAnswerVariantId);
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveyLogicTrigger.class, null, countFetches, cause, params) : null;

        EntityListResponse<SurveyLogicTriggerDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveyLogicTrigger> items = idObjectService.getList(SurveyLogicTrigger.class, null, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (SurveyLogicTrigger e : items) {
            SurveyLogicTriggerDTO el = SurveyLogicTriggerDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyLogicTrigger saveSurveyLogicTrigger(SurveyLogicTriggerDTO dto) throws ObjectNotFoundException, BadDTOException {
        SurveyLogicTrigger entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveyLogicTrigger.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveyLogicTrigger();
        }
        entity.setSurveyPage(idObjectService.getObjectById(SurveyPage.class, dto.getSurveyPageId()));
        entity.setSurveyQuestion(idObjectService.getObjectById(SurveyQuestion.class, dto.getSurveyQuestionId()));
        entity.setAnswerVariant(idObjectService.getObjectById(SurveyAnswerVariant.class, dto.getAnswerVariantId()));
        entity.setSurveyLogicActionType(ds.get(SurveyLogicActionType.class, dto.getLogicActionTypeId()));
        entity.setNewConclusion(dto.getNewConclusion());
        entity.setNewLink(dto.getNewLink());

        if (dto.getPageIndex() == null && dto.getLogicActionTypeId().equals(DataConstants.LogicActionTypes.GO_TO_PAGE.getValue())) {
            throw new BadDTOException("expected page index, received null");
        }

        entity.setPageIndex(dto.getPageIndex());
        entity.setInteractionRequired(dto.getInteractionRequired());
        entity.setTargetQuestion(idObjectService.getObjectById(SurveyQuestion.class, dto.getTargetQuestionId()));

        return idObjectService.save(entity);
    }

    @Override
    public SurveyLogicTriggerDTO getSurveyLogicTrigger(UUID id) throws ObjectNotFoundException {
        SurveyLogicTrigger entity = idObjectService.getObjectById(SurveyLogicTrigger.class, id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveyLogicTriggerDTO.prepare(entity);
    }

    @Override
    public EntityListResponse<SurveySessionDTO> getSurveySessionsPaged(UUID surveyId, UUID userId, String lastVisitIP, boolean calculate,
                                                                       Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (!StringUtils.isEmpty(lastVisitIP)) {
            params.put("ip", "%%" + lastVisitIP + "%%");
            cause += "and el.lastVisitIP like :ip ";
        }

        if (userId != null) {
            cause += "and el.user.id=:userId ";
            params.put("userId", userId);
        }

        if (surveyId != null) {
            cause += "and el.survey.id=:surveyId ";
            params.put("surveyId", surveyId);
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveySession.class, null, countFetches, cause, params) : null;

        EntityListResponse<SurveySessionDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveySession> items = idObjectService.getList(SurveySession.class, null, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (SurveySession e : items) {
            SurveySessionDTO el = SurveySessionDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveySession saveSurveySession(SurveySessionDTO dto) throws ObjectNotFoundException {
        SurveySession entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveySession.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveySession();
        }

        entity.setUser(idObjectService.getObjectById(User.class, dto.getUserId()));
        entity.setLastVisitIP(dto.getLastVisitIP());
        entity.setSurvey(idObjectService.getObjectById(Survey.class, dto.getSurveyId()));
        entity.setStarted(dto.getStarted());
        entity.setEnded(dto.getEnded());
        entity.setExpirationDate(dto.getExpirationDate());
        entity.setPageVisitHistory(dto.getPageVisitHistory());

        return idObjectService.save(entity);
    }

    @Override
    public SurveySessionDTO getSurveySession(UUID id) throws ObjectNotFoundException {
        SurveySession entity = idObjectService.getObjectById(SurveySession.class, id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveySessionDTO.prepare(entity);
    }

    @Override
    public EntityListResponse<SurveyQuestionAnswerDTO> getSurveyQuestionAnswersPaged(UUID surveySessionId, boolean calculate,
                                                                                     Integer count, Integer page, Integer start,
                                                                                     String sortField, String sortDir) {
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (surveySessionId != null) {
            cause += String.format("and el.surveySession = '%s' ", surveySessionId);
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveySession.class, null, countFetches, cause, params) : null;

        EntityListResponse<SurveyQuestionAnswerDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveyQuestionAnswer> items = idObjectService.getList(SurveyQuestionAnswer.class, null, cause, params,
                sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (SurveyQuestionAnswer e : items) {
            SurveyQuestionAnswerDTO el = SurveyQuestionAnswerDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyQuestionAnswer saveSurveyQuestionAnswer(SurveyQuestionAnswerDTO dto) throws ObjectNotFoundException {
        SurveyQuestionAnswer entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveyQuestionAnswer.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveyQuestionAnswer();
        }

        entity.setSurveySession(idObjectService.getObjectById(SurveySession.class, dto.getSurveySessionId()));
        entity.setQuestion(idObjectService.getObjectById(SurveyQuestion.class, dto.getQuestionId()));
        entity.setAnswerVariant(idObjectService.getObjectById(SurveyAnswerVariant.class, dto.getAnswerVariantId()));
        entity.setSurveyPage(idObjectService.getObjectById(SurveyPage.class, dto.getSurveyPageId()));
        entity.setText(dto.getText());
        entity.setStoredFile(idObjectService.getObjectById(StoredFile.class, dto.getStoredFile()));
        entity.setSelectedMatrixColumn(dto.getSelectedMatrixColumn());
        entity.setSelectedMatrixRow(dto.getSelectedMatrixRow());

        return idObjectService.save(entity);
    }

    @Override
    public SurveyQuestionAnswerDTO getSurveyQuestionAnswer(UUID id) throws ObjectNotFoundException {
        SurveyQuestionAnswer entity = idObjectService.getObjectById(SurveyQuestionAnswer.class, id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveyQuestionAnswerDTO.prepare(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurvey(UUID id, boolean deleteAnswers) throws LogicDependencyException, ResultDependencyException {
        Map<String, Object> params = new HashMap<>();
        params.put("surveyId", id);
        List<SurveyPage> pages = idObjectService.getList(SurveyPage.class, null,
                "el.survey.id=:surveyId", params, null, null, null);

        for (SurveyPage page : pages) {
            deleteSurveyPage(page.getId(), deleteAnswers);
        }

        if (deleteAnswers) {
            idObjectService.delete(SurveySession.class, "el.survey.id = :surveyId", params);
        }

        idObjectService.delete(Survey.class, id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurveyPage(UUID id, boolean deleteAnswers) throws LogicDependencyException, ResultDependencyException {
        Map<String, Object> params = new HashMap<>();
        params.put("surveyPageId", id);

        List<SurveyQuestion> surveyQuestions = idObjectService.getList(SurveyQuestion.class, null,
                "el.surveyPage.id=:surveyPageId", params, null, null, null);

        for (SurveyQuestion question : surveyQuestions) {
            // если удаляется целая страница - подразумевается, что удаляется вся логика вместе с ней
            deleteSurveyQuestion(question.getId(), true, deleteAnswers);
        }

        idObjectService.delete(SurveyPage.class, id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurveyQuestion(UUID id, boolean deleteLogic, boolean deleteAnswers) throws LogicDependencyException, ResultDependencyException {
        Map<String, Object> params = new HashMap<>();
        params.put("questionId", id);

        if (!deleteAnswers) {
            boolean isQuestionAnswered = idObjectService.checkExist(SurveyQuestionAnswer.class, null, "el.question.id=:questionId",
                    params, 1) > 0;

            if (isQuestionAnswered) {
                throw new ResultDependencyException("Question is answered");
            }
        } else {
            // delete all answers
            idObjectService.delete(SurveyQuestionAnswer.class, "el.surveyQuestion.id=:questionId", params);
        }

        if (!deleteLogic) {
            boolean hasLogic = idObjectService.checkExist(SurveyLogicTrigger.class, null,
                    "el.surveyQuestion.id=:questionId or el.targetQuestion.id=:questionId", params, 1) > 0;

            if (hasLogic) {
                throw new LogicDependencyException("Question has corresponding logic");
            }
        } else {
            List<SurveyLogicTrigger> logicTriggers = idObjectService.getList(SurveyLogicTrigger.class, null,
                    "el.surveyQuestion.id=:questionId or el.targetQuestion.id=:questionId",
                    params, null, null, null);

            for (SurveyLogicTrigger trigger : logicTriggers) {
                deleteSurveyLogicTrigger(trigger.getId());
            }
        }

        List<SurveyAnswerVariant> answerVariants = idObjectService.getList(SurveyAnswerVariant.class, null,
                "el.surveyQuestion.id=:questionId", params, null, null, null);

        for (SurveyAnswerVariant var : answerVariants) {
            deleteSurveyAnswerVariant(var.getId(), deleteLogic, deleteAnswers);
        }

        idObjectService.delete(SurveyQuestion.class, id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurveyAnswerVariant(UUID id, boolean deleteLogic, boolean deleteAnswers) throws LogicDependencyException, ResultDependencyException {
        Map<String, Object> params = new HashMap<>();
        params.put("answerVariantId", id);

        if (!deleteAnswers) {
            boolean isAnswered = idObjectService.checkExist(SurveyQuestionAnswer.class, null, "el.answerVariant.id=:answerVariantId",
                    params, 1) > 0;
            if (isAnswered) {
                throw new ResultDependencyException("Selected as answer");
            }
        }

        if (!deleteLogic) {
            boolean hasLogic = idObjectService.checkExist(SurveyLogicTrigger.class, null,
                    "el.answerVariant.id=:answerVariantId", params, 1) > 0;

            if (hasLogic) {
                throw new LogicDependencyException("answer has corresponding logic");
            }
        } else {
            List<SurveyLogicTrigger> logicTriggers = idObjectService.getList(SurveyLogicTrigger.class, null,
                    "el.answerVariant.id=:answerVariantId",
                    params, null, null, null);

            for (SurveyLogicTrigger trigger : logicTriggers) {
                deleteSurveyLogicTrigger(trigger.getId());
            }
        }

        idObjectService.delete(SurveyAnswerVariant.class, id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurveyLogicTrigger(UUID id) {
        idObjectService.delete(SurveyLogicTrigger.class, id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurveySession(UUID id) {
        Map<String, Object> params = new HashMap<>();
        params.put("surveySessionId", id);

        idObjectService.delete(SurveyQuestionAnswer.class, "el.surveySession.id=:surveySessionId", params);
        idObjectService.delete(SurveyLogicTrigger.class, id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurveyQuestionAnswer(UUID id) {
        idObjectService.delete(SurveyQuestionAnswer.class, id);
    }

    @Override
    public EntityListResponse<SurveyAnswerVariantCatalogDTO> getCatalogsPaged(String name, boolean calculate, Integer count, Integer page,
                                                                              Integer start, String sortField, String sortDir) {
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (!StringUtils.isEmpty(name)) {
            params.put("name", "%%" + StringUtils.lowerCase(name) + "%%");
            cause += "and lower(el.name) like :name ";
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveyAnswerVariantCatalog.class, null, null, cause, params) : null;

        EntityListResponse<SurveyAnswerVariantCatalogDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveyAnswerVariantCatalog> items = idObjectService.getList(SurveyAnswerVariantCatalog.class, null, cause,
                params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (SurveyAnswerVariantCatalog e : items) {
            SurveyAnswerVariantCatalogDTO el = SurveyAnswerVariantCatalogDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    public SurveyAnswerVariantCatalogDTO getCatalog(UUID id) throws ObjectNotFoundException {
        SurveyAnswerVariantCatalog entity = idObjectService.getObjectById(SurveyAnswerVariantCatalog.class, id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveyAnswerVariantCatalogDTO.prepare(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyAnswerVariantCatalog saveCatalog(SurveyAnswerVariantCatalogDTO dto) throws ObjectNotFoundException {
        SurveyAnswerVariantCatalog entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveyAnswerVariantCatalog.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveyAnswerVariantCatalog();
        }
        Map<String, Object> params = new HashMap<>();
        if (dto.getItemsToDelete() != null && dto.getItemsToDelete().size() > 0) {
            params.put("itemIds", dto.getItemsToDelete());
            idObjectService.delete(SurveyAnswerVariantCatalogItem.class, "el.id in (:itemIds)", params);
        }
        params.clear();

        if (dto.getItems() != null && dto.getItems().size() > 0) {
            for (SurveyAnswerVariantCatalogItemDTO itemDTO : dto.getItems()) {
                saveCatalogItem(itemDTO);
            }
        }

        entity.setName(dto.getName());
        entity.setExternal(dto.isExternal());
        entity.setSuggestionProcessorName(dto.getSuggestionProcessorName());
        if (dto.getSuggestionProcessorName() == null) {
            entity.setSuggestionProcessorName("basicVariantSuggestionProcessor"); // TODO: to cmn_property?
        }

        SurveyAnswerVariantCatalog catalog = idObjectService.save(entity);

        // if it's external catalog now and still have items - delete them
        if (catalog.isExternal()) {
            params.clear();
            params.put("id", catalog.getId());
            idObjectService.delete(SurveyAnswerVariantCatalogItem.class, "el.catalog.id = :id", params);
        }

        return catalog;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteCatalog(UUID id) {
        Map<String, Object> params = new HashMap<>();
        params.put("catalogId", id);
        idObjectService.delete(SurveyAnswerVariantCatalogItem.class, "el.catalog.id=:catalogId", params);

        idObjectService.delete(SurveyAnswerVariantCatalog.class, id);
    }

    @Override
    public EntityListResponse<SurveyAnswerVariantCatalogItemDTO> getCatalogItemsPaged(UUID catalogId, String text, boolean calculate, Integer count, Integer page,
                                                                                      Integer start, String sortField, String sortDir) {
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();

        if (catalogId != null) {
            cause += "and el.catalog.id=:catalogId ";
            params.put("catalogId", catalogId);
        }

        if (!StringUtils.isEmpty(text)) {
            params.put("text", "%%" + StringUtils.lowerCase(text) + "%%");
            cause += "and lower(el.text) like :text ";
        }

        Integer totalCount = calculate ? idObjectService.getCount(SurveyAnswerVariantCatalogItem.class, null, null, cause, params) : null;

        EntityListResponse<SurveyAnswerVariantCatalogItemDTO> entityListResponse = new EntityListResponse<>(totalCount, count, page, start);

        List<SurveyAnswerVariantCatalogItem> items = idObjectService.getList(SurveyAnswerVariantCatalogItem.class, null, cause,
                params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        for (SurveyAnswerVariantCatalogItem e : items) {
            SurveyAnswerVariantCatalogItemDTO el = SurveyAnswerVariantCatalogItemDTO.prepare(e);
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    public SurveyAnswerVariantCatalogItemDTO getCatalogItem(UUID id) throws ObjectNotFoundException {
        SurveyAnswerVariantCatalogItem entity = idObjectService.getObjectById(SurveyAnswerVariantCatalogItem.class, id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        return SurveyAnswerVariantCatalogItemDTO.prepare(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyAnswerVariantCatalogItem saveCatalogItem(SurveyAnswerVariantCatalogItemDTO dto) throws ObjectNotFoundException {
        SurveyAnswerVariantCatalogItem entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(SurveyAnswerVariantCatalogItem.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new SurveyAnswerVariantCatalogItem();
        }

        SurveyAnswerVariantCatalog catalog = idObjectService.getObjectById(SurveyAnswerVariantCatalog.class, dto.getCatalogId());
        if (catalog == null) throw new ObjectNotFoundException();

        entity.setText(dto.getText());
        entity.setCatalog(catalog);

        return idObjectService.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteCatalogItem(UUID id) {
        idObjectService.delete(SurveyAnswerVariantCatalogItem.class, id);
    }
}
