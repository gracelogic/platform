package com.gracelogic.platform.market.service;

import com.gracelogic.platform.account.dto.CurrencyDTO;
import com.gracelogic.platform.account.exception.AccountNotFoundException;
import com.gracelogic.platform.account.exception.CurrencyMismatchException;
import com.gracelogic.platform.account.exception.InsufficientFundsException;
import com.gracelogic.platform.account.exception.NoActualExchangeRateException;
import com.gracelogic.platform.account.model.Account;
import com.gracelogic.platform.account.model.Currency;
import com.gracelogic.platform.account.model.ExchangeRate;
import com.gracelogic.platform.account.service.AccountService;
import com.gracelogic.platform.db.dto.EntityListResponse;
import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.dictionary.service.DictionaryService;
import com.gracelogic.platform.finance.FinanceUtils;
import com.gracelogic.platform.market.DataConstants;
import com.gracelogic.platform.market.dao.MarketDao;
import com.gracelogic.platform.market.dto.*;
import com.gracelogic.platform.market.exception.*;
import com.gracelogic.platform.market.model.*;
import com.gracelogic.platform.payment.dto.PaymentExecutionResultDTO;
import com.gracelogic.platform.payment.exception.InvalidPaymentSystemException;
import com.gracelogic.platform.payment.exception.PaymentExecutionException;
import com.gracelogic.platform.payment.model.Payment;
import com.gracelogic.platform.payment.model.PaymentSystem;
import com.gracelogic.platform.payment.service.AccountResolver;
import com.gracelogic.platform.payment.service.PaymentExecutor;
import com.gracelogic.platform.user.dto.AuthorizedUser;
import com.gracelogic.platform.user.exception.ForbiddenException;
import com.gracelogic.platform.user.model.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Service
public class MarketServiceImpl implements MarketService {

    private static Logger logger = Logger.getLogger(MarketServiceImpl.class);

    @Autowired
    private IdObjectService idObjectService;

    @Autowired
    private DictionaryService ds;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountResolver accountResolver;

    @Autowired
    private MarketDao marketDao;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MarketResolver marketResolver;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Order saveOrder(OrderDTO dto, AuthorizedUser authorizedUser) throws InvalidOrderStateException, OrderNotConsistentException, ObjectNotFoundException, ForbiddenException, InvalidDiscountException, NoActualExchangeRateException {
        Order entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(Order.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
            if (!authorizedUser.getGrants().contains("ORDER:SAVE") && !entity.getUser().getId().equals(authorizedUser.getId())) {
                throw new ForbiddenException();
            }
            if (!entity.getOrderState().getId().equals(DataConstants.OrderStates.DRAFT.getValue())) {
                throw new InvalidOrderStateException();
            }

            //Delete order products
            Map<String, Object> params = new HashMap<>();
            params.put("orderId", entity.getId());
            idObjectService.delete(OrderProduct.class, "el.order.id=:orderId", params);
        } else {
            entity = new Order();
            entity.setOrderState(ds.get(OrderState.class, DataConstants.OrderStates.DRAFT.getValue()));
            entity.setUser(idObjectService.getObjectById(User.class, authorizedUser.getId()));
            entity.setPaid(0L);
        }

        Set<UUID> productIds = new HashSet<>();
        Set<UUID> discountProductIds = new HashSet<>();
        //Find discount
        Discount discount = null;
        if (!StringUtils.isEmpty(dto.getDiscountSecretCode())) {
            discount = getActiveDiscountBySecretCode(dto.getDiscountSecretCode());
            if (discount == null) {
                throw new InvalidDiscountException();
            }
        } else if (dto.getDiscountId() != null) {
            discount = idObjectService.getObjectById(Discount.class, dto.getDiscountId());
        }
        if (discount != null && discount.getDiscountType().getId().equals(DataConstants.DiscountTypes.GIFT_PRODUCT.getValue())) {
            Map<String, Object> params = new HashMap<>();
            params.put("discountId", discount.getId());
            List<DiscountProduct> discountProducts = idObjectService.getList(DiscountProduct.class, null, "el.discount.id=:discountId", params, null, null, null, null);
            for (DiscountProduct discountProduct : discountProducts) {
                discountProductIds.add(discountProduct.getProduct().getId());
            }
        }


        //Get products to purchase
        productIds.addAll(discountProductIds);
        for (ProductDTO productDTO : dto.getProducts()) {
            productIds.add(productDTO.getId());
        }

        if (productIds.isEmpty()) {
            throw new OrderNotConsistentException("No products found in order");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("productIds", productIds);
        List<Product> products = idObjectService.getList(Product.class, null, "el.id in (:productIds)", params, null, null, null, productIds.size());
        UUID targetCurrencyId = dto.getTargetCurrencyId();
        if (targetCurrencyId == null) {
            throw new OrderNotConsistentException("No target currency");
        }

        //Calculate total amount
        Long amount = calculateOrderTotalAmount(products, targetCurrencyId);

        Long discountAmount = 0L;

        //Apply discount
        if (discount != null) {
            if (discount.getDiscountType().getId().equals(DataConstants.DiscountTypes.FIX_AMOUNT_DISCOUNT.getValue())) {
                if (discount.getAmount() == null || discount.getCurrency() == null) {
                    throw new InvalidDiscountException();
                }
                discountAmount = discount.getAmount();
                if (!discount.getCurrency().getId().equals(targetCurrencyId)) {
                    ExchangeRate exchangeRate = accountService.getActualExchangeRate(discount.getCurrency().getId(), targetCurrencyId, null);
                    discountAmount = FinanceUtils.toDecimal(FinanceUtils.toFractional(exchangeRate.getValue()) * FinanceUtils.toFractional(discount.getAmount()));
                }

                if (discountAmount > amount) {
                    discountAmount = amount;
                }
            } else if (discount.getDiscountType().getId().equals(DataConstants.DiscountTypes.FIX_PERCENT_DISCOUNT.getValue())) {
                Long hundredPercents = FinanceUtils.toDecimal(100D);
                if (discount.getAmount() == null || discount.getAmount() > hundredPercents) {
                    throw new InvalidDiscountException();
                }
                double dDiscountAmount = (FinanceUtils.toFractional(amount) / 100D) * FinanceUtils.toFractional(discount.getAmount());
                discountAmount = FinanceUtils.toDecimal(dDiscountAmount);

            } else if (discount.getDiscountType().getId().equals(DataConstants.DiscountTypes.GIFT_PRODUCT.getValue())) {
                List<Product> onlyDiscountedProducts = new LinkedList<>();
                for (Product product : products) {
                    if (discountProductIds.contains(product.getId())) {
                        onlyDiscountedProducts.add(product);
                    }
                }
                discountAmount = calculateOrderTotalAmount(onlyDiscountedProducts, targetCurrencyId);
            }
        }

        Long totalAmount = amount - discountAmount;
        entity.setAmount(amount);
        entity.setDiscountAmount(discountAmount);
        entity.setTotalAmount(FinanceUtils.toDecimal(FinanceUtils.toFractional2Rounded(totalAmount))); //Принудительно делаем 2 знака после запятой
        entity.setDiscount(discount);
        entity.setTargetCurrency(ds.get(Currency.class, targetCurrencyId));
        entity = idObjectService.save(entity);

        //Create order products
        for (UUID productId : productIds) {
            Product product = null;
            for (Product p : products) {
                if (p.getId().equals(productId)) {
                    product = p;
                    break;
                }
            }
            if (product == null || !product.getActive()) {
                throw new OrderNotConsistentException("Product not found or not active: " + productId);
            }

            OrderProduct orderProduct = new OrderProduct();
            orderProduct.setOrder(entity);
            orderProduct.setProduct(product);
            idObjectService.save(orderProduct);
        }

        return entity;
    }

    private Long calculateOrderTotalAmount(List<Product> products, UUID targetCurrencyId) throws OrderNotConsistentException, NoActualExchangeRateException {
        //TODO: Можно оптимизировать этот метод закэшировав ExchangeRates вместо того что бы для каждого продукта их доставать

        Long amount = 0L;
        for (Product product : products) {
            Long price = product.getPrice();
            if (!product.getCurrency().getId().equals(targetCurrencyId)) {
                ExchangeRate exchangeRate = accountService.getActualExchangeRate(product.getCurrency().getId(), targetCurrencyId, null);
                price = FinanceUtils.toDecimal(FinanceUtils.toFractional(exchangeRate.getValue()) * FinanceUtils.toFractional(product.getPrice()));
            }
            amount += price;
        }

        return amount;
    }

    @Override
    public List<CurrencyDTO> getAvailableCurrencies() {
        List<CurrencyDTO> currencyDTOs = new LinkedList<>();
        List<MerchantAccount> merchantAccounts = idObjectService.getList(MerchantAccount.class, "left join fetch el.currency", null, null, null, null, null, null);
        for (MerchantAccount merchantAccount : merchantAccounts) {
            CurrencyDTO dto = CurrencyDTO.prepare(merchantAccount.getCurrency());
            currencyDTOs.add(dto);
        }

        return currencyDTOs;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void queueCashierVoucher(UUID cashierVoucherTypeId, Order order) {
        CashierVoucher cashierVoucher = new CashierVoucher();
        cashierVoucher.setCashierVoucherType(ds.get(CashierVoucherType.class, cashierVoucherTypeId));
        cashierVoucher.setOrder(order);
        cashierVoucher.setProcessed(false);
        idObjectService.save(cashierVoucher);
    }


    private Discount getActiveDiscountBySecretCode(String secretCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("secretCode", StringUtils.trim(secretCode));
        params.put("active", true);

        List<Discount> discounts = idObjectService.getList(Discount.class, null, "el.secretCode=:secretCode and el.active=:active and ((el.reusable=false and el.used=false) or el.reusable=true)", params, null, null, null, 1);
        return discounts.isEmpty() ? null : discounts.iterator().next();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PaymentExecutionResultDTO executeOrder(UUID orderId, UUID paymentSystemId, Map<String, String> params, AuthorizedUser authorizedUser) throws InvalidOrderStateException, OrderNotConsistentException, ForbiddenException, InvalidPaymentSystemException, AccountNotFoundException, InsufficientFundsException, InvalidDiscountException, ObjectNotFoundException, PaymentExecutionException, CurrencyMismatchException {
        Order order = idObjectService.getObjectById(Order.class, orderId);
        if (order == null) {
            throw new ObjectNotFoundException();
        }

        if (!authorizedUser.getGrants().contains("ORDER:EXECUTE") && !order.getUser().getId().equals(authorizedUser.getId())) {
            throw new ForbiddenException();
        }

        if (!order.getOrderState().getId().equals(DataConstants.OrderStates.DRAFT.getValue()) &&
                !order.getOrderState().getId().equals(DataConstants.OrderStates.PENDING.getValue())) {
            throw new InvalidOrderStateException();
        }

        if (order.getOrderState().getId().equals(DataConstants.OrderStates.DRAFT.getValue())) {
            order.setOrderState(ds.get(OrderState.class, DataConstants.OrderStates.PENDING.getValue()));
            if (StringUtils.isEmpty(order.getExternalIdentifier())) {
                order.setExternalIdentifier(order.getId().toString());
            }
            order = idObjectService.save(order);

            //Check and process discount
            if (order.getDiscount() != null) {
                Discount discount = idObjectService.getObjectById(Discount.class, order.getDiscount().getId());
                if (!discount.getReusable()) {
                    if (discount.getUsed()) {
                        throw new InvalidDiscountException("This discount already used");
                    } else {
                        discount.setUsed(true);
                        discount.setUsedForOrder(order);
                        idObjectService.save(discount);
                    }
                }
            }

            //Update lifetime expiration
            recalculateOrderProductLifetimeExpiration(order, System.currentTimeMillis());
        }


        //Пытаемся оплатить с помощью внутреннего счёта
        Account userAccount = accountResolver.getTargetAccount(order.getUser(), null, null, ds.get(Currency.class, order.getTargetCurrency().getId()).getCode());
        Long amountToPay = order.getTotalAmount() - order.getPaid();
        if (userAccount.getBalance() >= amountToPay) {
            order = payOrder(order, amountToPay, userAccount.getId());
            amountToPay = order.getTotalAmount() - order.getPaid();
        }

        //Всё что недоплачено пытаемся получить через платёжную систему
        if (order.getOrderState().getId().equals(DataConstants.OrderStates.PENDING.getValue())) {
            PaymentSystem paymentSystem = idObjectService.getObjectById(PaymentSystem.class, paymentSystemId);
            if (paymentSystem == null || !paymentSystem.getActive() || StringUtils.isEmpty(paymentSystem.getPaymentExecutorClass())) {
                throw new InvalidPaymentSystemException();
            }

            boolean orderModified = false;
            if (order.getPaymentSystem() == null || !order.getPaymentSystem().getId().equals(paymentSystemId)) {
                order.setPaymentSystem(paymentSystem);
                orderModified = true;
            }

            PaymentExecutionResultDTO result = null;
            try {
                PaymentExecutor paymentExecutor = initializePaymentExecutor(paymentSystem.getPaymentExecutorClass());
                result = paymentExecutor.execute(String.valueOf(order.getId()), paymentSystemId, amountToPay, ds.get(Currency.class, order.getTargetCurrency().getId()).getCode(), applicationContext, params);
                if (!StringUtils.isEmpty(result.getExternalIdentifier()) && !StringUtils.equals(order.getExternalIdentifier(), result.getExternalIdentifier())) {
                    order.setExternalIdentifier(result.getExternalIdentifier());
                    orderModified = true;
                }
            } catch (PaymentExecutionException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                throw new PaymentExecutionException(e.getMessage());
            }

            if (orderModified) {
                order = idObjectService.save(order);
            }

            return result;
        }

        return new PaymentExecutionResultDTO(order.getOrderState().getId().equals(DataConstants.OrderStates.PAID.getValue()));
    }

    private PaymentExecutor initializePaymentExecutor(String paymentExecutorClassName) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<?> clazz = Class.forName(paymentExecutorClassName);
        return (PaymentExecutor) clazz.newInstance();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelOrder(UUID orderId) throws InvalidOrderStateException, ObjectNotFoundException, InsufficientFundsException, AccountNotFoundException, CurrencyMismatchException {
        Order order = idObjectService.getObjectById(Order.class, orderId);
        if (order == null) {
            throw new ObjectNotFoundException();
        }
        if (!order.getOrderState().getId().equals(DataConstants.OrderStates.PAID.getValue())) {
            throw new InvalidOrderStateException();
        }

        Long amountToReturn = order.getPaid();

        //Transfer money from organization to user
        Account userAccount = accountResolver.getTargetAccount(order.getUser(), null, null, ds.get(Currency.class, order.getTargetCurrency().getId()).getCode());
        UUID merchantAccountId = getMerchantAccountId(order.getTargetCurrency().getId());

        accountService.processTransfer(merchantAccountId, com.gracelogic.platform.payment.DataConstants.TransactionTypes.MARKET_SELL_CANCEL.getValue(),
                userAccount.getId(), com.gracelogic.platform.payment.DataConstants.TransactionTypes.MARKET_BUY_CANCEL.getValue(),
                amountToReturn, order.getId(), false);

        order.setPaid(order.getPaid() - amountToReturn);
        order.setOrderState(ds.get(OrderState.class, DataConstants.OrderStates.CANCELED.getValue()));
        idObjectService.save(order);
        queueCashierVoucher(DataConstants.CashierVoucherTypes.INCOME_RETURN.getValue(), order);

        //Return discount
        if (order.getDiscount() != null) {
            Discount discount = idObjectService.getObjectById(Discount.class, order.getDiscount().getId());
            if (!discount.getReusable() && discount.getUsed() && discount.getUsedForOrder().getId().equals(orderId)) {
                discount.setUsed(false);
                discount.setUsedForOrder(null);
                idObjectService.save(discount);
            }
        }
    }

    private UUID getMerchantAccountId(UUID currencyId) throws AccountNotFoundException {
        Map<String, Object> params = new HashMap<>();
        params.put("currencyId", currencyId);
        List<MerchantAccount> accounts = idObjectService.getList(MerchantAccount.class, null, "el.currency.id=:currencyId", params, null, null, null, 1);
        if (accounts.isEmpty()) {
            throw new AccountNotFoundException();
        } else {
            return accounts.iterator().next().getAccount().getId();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processPayment(Payment payment) throws InvalidOrderStateException, AccountNotFoundException, InsufficientFundsException, CurrencyMismatchException {
        Map<String, Object> params = new HashMap<>();
        params.put("externalIdentifier", payment.getExternalIdentifier());

        params.put("orderStateId", DataConstants.OrderStates.PENDING.getValue());
        List<Order> orders = idObjectService.getList(Order.class, null, "el.externalIdentifier=:externalIdentifier and el.orderState.id=:orderStateId", params, null, null, null, 1);
        if (!orders.isEmpty()) {
            Order order = orders.iterator().next();
            Long amountToPay = order.getTotalAmount() - order.getPaid();
            if (amountToPay > payment.getAmount()) {
                amountToPay = payment.getAmount();
            }
            //Transfer money from user to organization
            payOrder(order, amountToPay, payment.getAccount().getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteOrder(UUID orderId, AuthorizedUser authorizedUser) throws InvalidOrderStateException, ObjectNotFoundException, ForbiddenException {
        Order order = idObjectService.getObjectById(Order.class, orderId);
        if (!authorizedUser.getGrants().contains("ORDER:DELETE") && !order.getUser().getId().equals(authorizedUser.getId())) {
            throw new ForbiddenException();
        }
        if (!order.getOrderState().getId().equals(DataConstants.OrderStates.DRAFT.getValue())) {
            throw new InvalidOrderStateException();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("orderId", orderId);
        idObjectService.delete(OrderProduct.class, "el.order.id=:orderId", params);
        idObjectService.delete(Order.class, orderId);
    }

    @Override
    public void checkAtLeastOneProductPurchased(UUID userId, Map<UUID, UUID> referenceObjectIdsAndProductTypeIds, Date checkOnDate) throws ProductNotPurchasedException {
        if (!marketDao.existAtLeastOneProductIsPurchased(userId, referenceObjectIdsAndProductTypeIds.keySet(), checkOnDate)) {
            throw new ProductNotPurchasedException();
        }
    }

    @Override
    public Map<UUID, List<PurchasedProductDTO>> getProductsPurchaseState(UUID userId, Map<UUID, UUID> referenceObjectIdsAndProductTypeIds, Date checkDate) {
        Map<UUID, List<PurchasedProductDTO>> result = new HashMap<>();

        List<OrderProduct> orderProducts = marketDao.getPurchasedProducts(userId, referenceObjectIdsAndProductTypeIds.keySet(), checkDate);

        for (UUID referenceObjectId : referenceObjectIdsAndProductTypeIds.keySet()) {
            UUID productTypeId = referenceObjectIdsAndProductTypeIds.get(referenceObjectId);

            List<PurchasedProductDTO> purchasedProducts = new LinkedList<>();
            for (OrderProduct orderProduct : orderProducts) {
                if (orderProduct.getProduct().getReferenceObjectId() != null
                        && orderProduct.getProduct().getReferenceObjectId().equals(referenceObjectId)
                        && orderProduct.getProduct().getProductType().getId().equals(productTypeId)) {
                    purchasedProducts.add(PurchasedProductDTO.prepare(orderProduct.getProduct(), true));
                }
            }
            result.put(referenceObjectId, purchasedProducts);
        }

        return result;
    }

    public Map<UUID, List<Product>> findProducts(Map<UUID, UUID> referenceObjectIdsAndProductTypeIds, boolean onlyPrimary) {
        Map<UUID, List<Product>> result = new HashMap<>();
        List<Product> products = marketDao.getProductsByReferenceObjectIds(referenceObjectIdsAndProductTypeIds.keySet(), onlyPrimary);

        for (UUID referenceObjectId : referenceObjectIdsAndProductTypeIds.keySet()) {
            UUID productTypeId = referenceObjectIdsAndProductTypeIds.get(referenceObjectId);
            List<Product> foundProducts = new LinkedList<>();
            for (Product product : products) {
                if (product.getReferenceObjectId() != null
                        && product.getReferenceObjectId().equals(referenceObjectId)
                        && product.getProductType().getId().equals(productTypeId)) {
                    foundProducts.add(product);

                    if (onlyPrimary) {
                        break;
                    }
                }
            }
            result.put(referenceObjectId, foundProducts);
        }

        return result;
    }

    public void enrichMarketInfo(UUID productTypeId, Collection<MarketAwareObjectDTO> objects, UUID relatedUserId, Date checkOnDate) {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        Map<UUID, UUID> referenceObjectIdsAndProductTypeIds = new HashMap<>();
        for (MarketAwareObjectDTO dto : objects) {
            if (dto.getId() == null) {
                continue;
            }
            referenceObjectIdsAndProductTypeIds.put(dto.getId(), productTypeId);
        }

        Map<UUID, List<PurchasedProductDTO>> purchased = Collections.emptyMap();
        if (relatedUserId != null) {
            purchased = getProductsPurchaseState(relatedUserId, referenceObjectIdsAndProductTypeIds, checkOnDate);
        }

        Map<UUID, List<Product>> products = findProducts(referenceObjectIdsAndProductTypeIds, false);

        for (MarketAwareObjectDTO dto : objects) {
            if (dto.getId() == null) {
                continue;
            }
            if (products.containsKey(dto.getId())) {
                List<Product> foundProducts = products.get(dto.getId());
                for (Product product : foundProducts) {
                    Boolean isPurchased = null;
                    if (purchased.containsKey(dto.getId())) {
                        isPurchased = false;
                        List<PurchasedProductDTO> purchasedProducts = purchased.get(dto.getId());
                        for (PurchasedProductDTO purchasedProduct : purchasedProducts) {
                            if (purchasedProduct.getId().equals(product.getId())) {
                                isPurchased = true;
                                break;
                            }
                        }
                    }
                    dto.getProducts().add(PurchasedProductDTO.prepare(product, isPurchased));
                }
            }
        }
    }

    private Order payOrder(Order order, Long amountToPay, UUID userAccountId) throws InsufficientFundsException, AccountNotFoundException, CurrencyMismatchException {
        UUID merchantAccountId = getMerchantAccountId(order.getTargetCurrency().getId());

        //Transfer money from user to organization
        accountService.processTransfer(userAccountId, com.gracelogic.platform.payment.DataConstants.TransactionTypes.MARKET_BUY.getValue(),
                merchantAccountId, com.gracelogic.platform.payment.DataConstants.TransactionTypes.MARKET_SELL.getValue(),
                amountToPay, order.getId(), false);

        order.setPaid(order.getPaid() + amountToPay);
        if (order.getPaid().equals(order.getTotalAmount())) {
            order.setOrderState(ds.get(OrderState.class, DataConstants.OrderStates.PAID.getValue()));
            order = idObjectService.save(order);
            queueCashierVoucher(DataConstants.CashierVoucherTypes.INCOME.getValue(), order);
            try {
                marketResolver.orderPaid(order);
            } catch (Exception e) {
                logger.warn("Failed to transmit order paid event", e);
            }
        }
        else {
            order = idObjectService.save(order);
        }
        return order;
    }

    private void recalculateOrderProductLifetimeExpiration(Order order, Long currentTimeMillis) {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", order.getId());

        List<OrderProduct> orderProducts = idObjectService.getList(OrderProduct.class, "left join fetch el.product", "el.order.id=:orderId", params, null, null, null, null);
        for (OrderProduct orderProduct : orderProducts) {
            if (orderProduct.getProduct().getLifetime() != null) {
                orderProduct.setLifetimeExpiration(new Date(currentTimeMillis + orderProduct.getProduct().getLifetime()));
            } else {
                orderProduct.setLifetimeExpiration(null);
            }
            idObjectService.save(orderProduct);
        }
    }

    @Override
    public OrderDTO getOrder(UUID id, boolean enrich, boolean withProducts, AuthorizedUser authorizedUser) throws ObjectNotFoundException, ForbiddenException {
        Order entity = idObjectService.getObjectById(Order.class, enrich ? "left join fetch el.user left join fetch el.orderState left join fetch el.discount left join fetch el.paymentSystem left join fetch el.targetCurrency" : "", id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        if (!authorizedUser.getGrants().contains("ORDER:SHOW") && !entity.getUser().getId().equals(authorizedUser.getId())) {
            throw new ForbiddenException();
        }
        OrderDTO dto = OrderDTO.prepare(entity);
        if (enrich) {
            OrderDTO.enrich(dto, entity);
        }
        if (withProducts) {
            Map<String, Object> params = new HashMap<>();
            params.put("orderId", id);
            List<OrderProduct> orderProducts = idObjectService.getList(OrderProduct.class, "left join fetch el.product", "el.order.id=:orderId", params, null, null, null, null);
            for (OrderProduct orderProduct : orderProducts) {
                if (orderProduct.getOrder().getId().equals(id)) {
                    dto.getProducts().add(ProductDTO.prepare(orderProduct.getProduct()));
                }
            }
        }
        return dto;
    }

    @Override
    public EntityListResponse<OrderDTO> getOrdersPaged(UUID userId, UUID orderStateId, UUID discountId, boolean enrich, boolean withProducts, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String fetches = enrich ? "left join fetch el.user left join fetch el.orderState left join fetch el.discount left join fetch el.paymentSystem left join fetch el.targetCurrency" : "";
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<String, Object>();

        if (userId != null) {
            cause += "and el.user.id=:userId ";
            params.put("userId", userId);
        }

        if (orderStateId != null) {
            cause += "and el.orderState.id=:orderStateId ";
            params.put("orderStateId", orderStateId);
        }

        if (discountId != null) {
            cause += "and el.discount.id=:discountId ";
            params.put("discountId", discountId);
        }

        int totalCount = idObjectService.getCount(Order.class, null, countFetches, cause, params);
        int totalPages = ((totalCount / count)) + 1;
        int startRecord = page != null ? (page * count) - count : start;

        EntityListResponse<OrderDTO> entityListResponse = new EntityListResponse<OrderDTO>();
        entityListResponse.setEntity("order");
        entityListResponse.setPage(page);
        entityListResponse.setPages(totalPages);
        entityListResponse.setTotalCount(totalCount);

        List<Order> items = idObjectService.getList(Order.class, fetches, cause, params, sortField, sortDir, startRecord, count);

        List<OrderProduct> orderProducts = Collections.emptyList();
        if (withProducts && !items.isEmpty()) {
            Set<UUID> orderIds = new HashSet<>();
            for (Order ord : items) {
                orderIds.add(ord.getId());
            }
            Map<String, Object> productParams = new HashMap<>();
            productParams.put("orderIds", orderIds);
            orderProducts = idObjectService.getList(OrderProduct.class, "left join fetch el.product", "el.order.id in (:orderIds)", productParams, null, null, null, null);
        }

        entityListResponse.setPartCount(items.size());
        for (Order e : items) {
            OrderDTO el = OrderDTO.prepare(e);
            if (enrich) {
                OrderDTO.enrich(el, e);
            }
            if (withProducts && !orderProducts.isEmpty()) {
                for (OrderProduct orderProduct : orderProducts) {
                    if (orderProduct.getOrder().getId().equals(e.getId())) {
                        el.getProducts().add(ProductDTO.prepare(orderProduct.getProduct()));
                    }
                }
            }
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    public ProductDTO getProduct(UUID id, boolean enrich) throws ObjectNotFoundException {
        Product entity = idObjectService.getObjectById(Product.class, enrich ? "left join fetch el.productType left join fetch el.currency left join fetch el.productOwnershipType" : "", id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        ProductDTO dto = ProductDTO.prepare(entity);
        if (enrich) {
            ProductDTO.enrich(dto, entity);
        }
        return dto;
    }

    @Override
    public EntityListResponse<ProductDTO> getProductsPaged(String name, UUID productTypeId, Boolean active, boolean enrich, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String fetches = enrich ? "left join fetch el.productType left join fetch el.currency left join fetch el.productOwnershipType" : "";
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<String, Object>();

        if (!StringUtils.isEmpty(name)) {
            params.put("name", "%%" + StringUtils.lowerCase(name) + "%%");
            cause += "and lower(el.name) like :name ";
        }
        if (productTypeId != null) {
            params.put("productTypeId", productTypeId);
            cause += "and el.productType.id = :productTypeId ";
        }
        if (active != null) {
            params.put("active", active);
            cause += "and el.active = :active ";
        }
        int totalCount = idObjectService.getCount(Product.class, null, countFetches, cause, params);
        int totalPages = ((totalCount / count)) + 1;
        int startRecord = page != null ? (page * count) - count : start;

        EntityListResponse<ProductDTO> entityListResponse = new EntityListResponse<ProductDTO>();
        entityListResponse.setEntity("product");
        entityListResponse.setPage(page);
        entityListResponse.setPages(totalPages);
        entityListResponse.setTotalCount(totalCount);

        List<Product> items = idObjectService.getList(Product.class, fetches, cause, params, sortField, sortDir, startRecord, count);
        entityListResponse.setPartCount(items.size());
        for (Product e : items) {
            ProductDTO el = ProductDTO.prepare(e);
            if (enrich) {
                ProductDTO.enrich(el, e);
            }
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Product saveProduct(ProductDTO dto) throws ObjectNotFoundException, PrimaryProductException {
        Product entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(Product.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new Product();
        }

        if ((entity.getId() != null && !entity.getPrimary() && dto.getPrimary()) || entity.getId() == null && dto.getPrimary()) {
            Map<String, Object> params = new HashMap<>();
            params.put("productTypeId", entity.getProductType().getId());
            params.put("referenceObjectId", entity.getReferenceObjectId());

            if (idObjectService.checkExist(Product.class, null, "el.productType.id=:productTypeId and el.referenceObjectId=:referenceObjectId and el.primary=true", params, 1) > 0) {
                throw new PrimaryProductException();
            }
        }

        entity.setName(dto.getName());
        entity.setActive(dto.getActive());
        entity.setProductType(ds.get(ProductType.class, dto.getProductTypeId()));
        entity.setReferenceObjectId(dto.getReferenceObjectId());
        entity.setLifetime(dto.getLifetime());
        entity.setPrice(dto.getPrice());
        entity.setPrimary(dto.getPrimary());
        entity.setCurrency(ds.get(Currency.class, dto.getCurrencyId()));
        entity.setProductOwnershipType(ds.get(ProductOwnershipType.class, dto.getProductTypeId()));

        return idObjectService.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteProduct(UUID id) throws ObjectNotFoundException {
        idObjectService.delete(Product.class, id);
    }

    @Override
    public DiscountDTO getDiscount(UUID id, boolean enrich, boolean withProducts) throws ObjectNotFoundException {
        Discount entity = idObjectService.getObjectById(Discount.class, enrich ? "left join fetch el.discountType left join fetch el.currency" : "", id);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        DiscountDTO dto = DiscountDTO.prepare(entity);
        if (enrich) {
            DiscountDTO.enrich(dto, entity);
        }
        if (withProducts) {
            Map<String, Object> params = new HashMap<>();
            params.put("discountId", id);
            List<DiscountProduct> discountProducts = idObjectService.getList(DiscountProduct.class, "left join fetch el.product", "el.discount.id=:discountId", params, null, null, null, null);
            for (DiscountProduct discountProduct : discountProducts) {
                if (discountProduct.getDiscount().getId().equals(id)) {
                    dto.getProducts().add(ProductDTO.prepare(discountProduct.getProduct()));
                }
            }
        }
        return dto;
    }

    @Override
    public EntityListResponse<DiscountDTO> getDiscountsPaged(String name, UUID usedForOrderId, UUID discountTypeId, boolean enrich, boolean withProducts, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String fetches = enrich ? "left join fetch el.discountType left join fetch el.currency" : "";
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<String, Object>();

        if (!StringUtils.isEmpty(name)) {
            params.put("name", "%%" + StringUtils.lowerCase(name) + "%%");
            cause += "and lower(el.name) like :name ";
        }

        if (usedForOrderId != null) {
            cause += "and el.usedForOrder.id=:usedForOrderId ";
            params.put("usedForOrderId", usedForOrderId);
        }

        if (discountTypeId != null) {
            cause += "and el.discountType.id=:discountTypeId ";
            params.put("discountTypeId", discountTypeId);
        }

        int totalCount = idObjectService.getCount(Discount.class, null, countFetches, cause, params);
        int totalPages = ((totalCount / count)) + 1;
        int startRecord = page != null ? (page * count) - count : start;

        EntityListResponse<DiscountDTO> entityListResponse = new EntityListResponse<DiscountDTO>();
        entityListResponse.setEntity("discount");
        entityListResponse.setPage(page);
        entityListResponse.setPages(totalPages);
        entityListResponse.setTotalCount(totalCount);

        List<Discount> items = idObjectService.getList(Discount.class, fetches, cause, params, sortField, sortDir, startRecord, count);

        List<DiscountProduct> discountProducts = Collections.emptyList();
        if (withProducts && !items.isEmpty()) {
            Set<UUID> discountIds = new HashSet<>();
            for (Discount dis : items) {
                discountIds.add(dis.getId());
            }
            Map<String, Object> productParams = new HashMap<>();
            productParams.put("discountIds", discountIds);
            discountProducts = idObjectService.getList(DiscountProduct.class, "left join fetch el.product", "el.discount.id in (:discountIds)", productParams, null, null, null, null);
        }

        entityListResponse.setPartCount(items.size());
        for (Discount e : items) {
            DiscountDTO el = DiscountDTO.prepare(e);
            if (enrich) {
                DiscountDTO.enrich(el, e);
            }
            if (withProducts && !discountProducts.isEmpty()) {
                for (DiscountProduct discountProduct : discountProducts) {
                    if (discountProduct.getDiscount().getId().equals(e.getId())) {
                        el.getProducts().add(ProductDTO.prepare(discountProduct.getProduct()));
                    }
                }
            }
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Discount saveDiscount(DiscountDTO dto) throws ObjectNotFoundException, CurrencyMismatchException {
        Discount entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(Discount.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new Discount();
            entity.setUsed(false);
        }

        if (entity.getId() != null) {
            String query = "el.discount.id=:discountId";
            HashMap<String, Object> params = new HashMap<>();
            params.put("discountId", entity.getId());
            idObjectService.delete(DiscountProduct.class, query, params);
        }

        if (dto.getDiscountTypeId().equals(DataConstants.DiscountTypes.FIX_AMOUNT_DISCOUNT.getValue()) && dto.getCurrencyId() == null) {
            throw new CurrencyMismatchException("Currency is required for this discount type");
        }

        entity.setName(dto.getName());
        entity.setActive(dto.getActive());
        entity.setReusable(dto.getReusable());
        entity.setDiscountType(ds.get(DiscountType.class, dto.getDiscountTypeId()));
        entity.setSecretCode(dto.getSecretCode());
        entity.setAmount(dto.getAmount());
        entity.setCurrency(ds.get(Currency.class, dto.getCurrencyId()));

        idObjectService.save(entity);

        for (ProductDTO productDTO : dto.getProducts()) {
            DiscountProduct dp = new DiscountProduct();
            dp.setDiscount(entity);
            dp.setProduct(idObjectService.getObjectById(Product.class, productDTO.getId()));
            idObjectService.save(dp);
        }

        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteDiscount(UUID id) throws ObjectNotFoundException {
        Map<String, Object> params = new HashMap<>();
        params.put("discountId", id);
        idObjectService.delete(DiscountProduct.class, "el.discount.id=:discountId", params);
        idObjectService.delete(Discount.class, id);
    }
}