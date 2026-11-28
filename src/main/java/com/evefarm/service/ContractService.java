package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.ContractDao;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.ContractItemDto;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.ContractItem;
import com.evefarm.model.ContractItemEntry;
import com.evefarm.model.ContractRow;
import com.evefarm.model.TypeInfo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class ContractService {

    private static final Logger LOG = Logger.getLogger(ContractService.class.getName());

    private final AuthService authService;
    private final ContractsApi contractsApi;
    private final EntityNameCacheService entityNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final ContractDao contractDao;
    private final TypeNameCacheService typeNameCacheService;
    private final PriceService priceService;

    public ContractService(AuthService authService, ContractsApi contractsApi,
                            EntityNameCacheService entityNameCacheService,
                            LocationNameCacheService locationNameCacheService, ContractDao contractDao,
                            TypeNameCacheService typeNameCacheService, PriceService priceService) {
        this.authService = authService;
        this.contractsApi = contractsApi;
        this.entityNameCacheService = entityNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.contractDao = contractDao;
        this.typeNameCacheService = typeNameCacheService;
        this.priceService = priceService;
    }

    public List<ContractItem> listContractItems(long characterId, long contractId) {
        return priced(contractDao.findItems(characterId, contractId).orElseGet(() -> {
            String accessToken = authService.getValidAccessToken(characterId);
            return fetchAndSaveItems(characterId, contractId, accessToken);
        }));
    }

    public List<ContractItem> priced(List<ContractItemEntry> items) {
        typeNameCacheService.resolveTypes(items.stream().map(ContractItemEntry::typeId).toList());
        return items.stream()
                .map(item -> {
                    TypeInfo type = typeNameCacheService.resolveType(item.typeId());
                    OptionalDouble price = item.blueprintCopy()
                            ? OptionalDouble.empty() : priceService.getUnitPrice(item.typeId());
                    return new ContractItem(item.typeId(),
                            item.blueprintCopy() ? type.name() + " (Blueprint Copy)" : type.name(),
                            type.groupName(), item.quantity(), item.included(),
                            price.isPresent() ? price.getAsDouble() : null);
                })
                .sorted(Comparator.comparing((ContractItem item) -> item.totalValue() == null ? 0 : item.totalValue())
                        .reversed()
                        .thenComparing(ContractItem::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public void refreshContractsForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<ContractDto> contracts = contractsApi.listContracts(characterId, accessToken);
        entityNameCacheService.resolveEntities(parties(contracts));
        locationNameCacheService.resolveLocations(locations(contracts), accessToken);
        contractDao.saveForCharacter(characterId, contracts.stream().map(ContractService::entry).toList());
        saveMissingItems(characterId, accessToken);
    }

    static Set<Long> parties(List<ContractDto> contracts) {
        Set<Long> entityIds = new HashSet<>();
        for (ContractDto contract : contracts) {
            Stream.of(contract.issuerId(), contract.assigneeId(), contract.acceptorId())
                    .filter(Objects::nonNull)
                    .forEach(id -> entityIds.add((long) id));
        }
        return entityIds;
    }

    static Set<Long> locations(List<ContractDto> contracts) {
        Set<Long> locationIds = new HashSet<>();
        for (ContractDto contract : contracts) {
            Stream.of(contract.startLocationId(), contract.endLocationId())
                    .filter(Objects::nonNull)
                    .forEach(locationIds::add);
        }
        return locationIds;
    }

    static ContractEntry entry(ContractDto c) {
        return new ContractEntry(c.contractId(), c.type(), c.status(), c.title(), c.collateral(), c.price(),
                c.reward(), c.volume(), c.dateIssued(), c.dateExpired(), c.dateCompleted(), c.forCorporation(),
                c.issuerId(), c.assigneeId(), c.acceptorId(), c.startLocationId(), c.endLocationId());
    }

    static ContractItemEntry itemEntry(ContractItemDto item) {
        return new ContractItemEntry(item.recordId(), item.typeId(), item.quantity(), item.rawQuantity(),
                item.included());
    }

    private void saveMissingItems(long characterId, String accessToken) {
        for (long contractId : contractDao.contractsWithoutItems(characterId)) {
            try {
                fetchAndSaveItems(characterId, contractId, accessToken);
            } catch (EsiException e) {
                if (e.statusCode() == 403 || e.statusCode() == 404) {
                    contractDao.markItemsUnavailable(characterId, contractId);
                } else {
                    LOG.log(Level.FINE, "Couldn't save the items of contract " + contractId + " yet", e);
                }
            }
        }
    }

    private List<ContractItemEntry> fetchAndSaveItems(long characterId, long contractId, String accessToken) {
        List<ContractItemEntry> items = contractsApi.listContractItems(characterId, contractId, accessToken).stream()
                .map(ContractService::itemEntry)
                .toList();
        typeNameCacheService.resolveTypes(items.stream().map(ContractItemEntry::typeId).toList());
        contractDao.saveItems(characterId, contractId, items);
        return items;
    }

    public List<ContractRow> getContractRows() {
        return contractDao.listRows();
    }
}
