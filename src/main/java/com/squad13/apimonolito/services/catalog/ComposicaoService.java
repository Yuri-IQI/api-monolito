package com.squad13.apimonolito.services.catalog;

import com.squad13.apimonolito.DTO.catalog.LoadCatalogParamsDTO;
import com.squad13.apimonolito.DTO.catalog.res.ResComposicaoDTO;
import com.squad13.apimonolito.DTO.catalog.res.ResPadraoDTO;
import com.squad13.apimonolito.exceptions.exceptions.InvalidCompositorException;
import com.squad13.apimonolito.exceptions.exceptions.ResourceNotFoundException;
import com.squad13.apimonolito.models.catalog.Padrao;
import com.squad13.apimonolito.models.catalog.associative.ComposicaoAmbiente;
import com.squad13.apimonolito.models.catalog.associative.ComposicaoMaterial;
import com.squad13.apimonolito.models.catalog.associative.ItemAmbiente;
import com.squad13.apimonolito.models.catalog.associative.MarcaMaterial;
import com.squad13.apimonolito.repository.catalog.*;
import com.squad13.apimonolito.util.enums.CompositorEnum;
import com.squad13.apimonolito.util.mapper.CatalogMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Transactional
@Service
@RequiredArgsConstructor
public class ComposicaoService {

    private final PadraoRepository padraoRepository;
    private final ComposicaoAmbienteRepository compAmbienteRepository;
    private final ComposicaoMaterialRepository compMaterialRepository;
    private final ItemAmbieteRepository itemAmbienteRepository;
    private final MarcaMaterialRepository marcaMaterialRepository;

    private final CatalogMapper catalogMapper;

    @PersistenceContext
    private final EntityManager em;

    private Padrao getPadrao(Long id) {
        return padraoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhum padrão encontrado com ID: " + id
                ));
    }

    private ItemAmbiente getItemAmbiente(Long id) {
        return itemAmbienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhuma associação de ambiente e item encontrada com ID: " + id
                ));
    }

    private MarcaMaterial getMarcaMaterial(Long id) {
        return marcaMaterialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhuma associação de marca e material encontrada com ID: " + id
                ));
    }

    private List<ResPadraoDTO> findPadraoByAmbiente(Long id, LoadCatalogParamsDTO params) {
        return compAmbienteRepository.findByCompositor_Ambiente_Id(id)
                .stream()
                .map(ComposicaoAmbiente::getPadrao)
                .distinct()
                .map(p -> catalogMapper.toResponse(p, params))
                .toList();
    }

    private List<ResPadraoDTO> findPadraoByMaterial(Long id, LoadCatalogParamsDTO params) {
        return compMaterialRepository.findByCompositor_Material_Id(id)
                .stream()
                .map(ComposicaoMaterial::getPadrao)
                .distinct()
                .map(p -> catalogMapper.toResponse(p, params))
                .toList();
    }

    private ResComposicaoDTO addAmbienteRelToPadrao(Long padraoId, Long itemAmbienteId) {
        Padrao padrao = getPadrao(padraoId);
        ItemAmbiente itemAmbiente = getItemAmbiente(itemAmbienteId);

        ComposicaoAmbiente comp = new ComposicaoAmbiente();
        comp.setPadrao(padrao);
        comp.setCompositor(itemAmbiente);

        compAmbienteRepository.save(comp);
        return catalogMapper.toCompDTO(comp);
    }

    private ResComposicaoDTO addMaterialRelToPadrao(Long padraoId, Long marcaMaterialId) {
        Padrao padrao = getPadrao(padraoId);
        MarcaMaterial marcaMaterial = getMarcaMaterial(marcaMaterialId);

        ComposicaoMaterial comp = new ComposicaoMaterial();
        comp.setPadrao(padrao);
        comp.setCompositor(marcaMaterial);

        compMaterialRepository.save(comp);
        return catalogMapper.toCompDTO(comp);
    }

    private List<ResComposicaoDTO> addAllAmbienteRelToPadrao(Long padraoId, Long ambienteId) {
        Padrao padrao = getPadrao(padraoId);

        List<ItemAmbiente> itens = itemAmbienteRepository.findByAmbiente_Id(ambienteId);
        if (itens.isEmpty()) {
            throw new ResourceNotFoundException("Nenhum associação de ambiente/item encontrado para o ambiente ID: " + ambienteId);
        }

        List<ComposicaoAmbiente> comps = itens.stream().map(item -> {
            ComposicaoAmbiente ca = new ComposicaoAmbiente();
            ca.setPadrao(padrao);
            ca.setCompositor(item);
            return ca;
        }).toList();

        compAmbienteRepository.saveAll(comps);
        return comps.stream().map(catalogMapper::toCompDTO).toList();
    }

    private List<ResComposicaoDTO> addAllMaterialRelToPadrao(Long padraoId, Long materialId) {
        Padrao padrao = getPadrao(padraoId);

        List<MarcaMaterial> marcas = marcaMaterialRepository.findByMaterial_Id(materialId);
        if (marcas.isEmpty()) {
            throw new ResourceNotFoundException("Nenhuma associação de material e marca encontrada para o material ID: " + materialId);
        }

        List<ComposicaoMaterial> comps = marcas.stream().map(marca -> {
            ComposicaoMaterial cm = new ComposicaoMaterial();
            cm.setPadrao(padrao);
            cm.setCompositor(marca);
            return cm;
        }).toList();

        compMaterialRepository.saveAll(comps);
        return comps.stream().map(catalogMapper::toCompDTO).toList();
    }

    private void removeAllAmbienteAssociationsFromPadrao(Long padraoId, Long ambienteId) {
        boolean removed = compAmbienteRepository.deleteByPadrao_IdAndCompositor_Ambiente_Id(padraoId, ambienteId);
        if (!removed) {
            throw new ResourceNotFoundException(
                    "Nenhuma composição de ambiente encontrada para o padrão "
                            + padraoId + " e ambiente " + ambienteId
            );
        }
    }

    private void removeAllMaterialAssociationsFromPadrao(Long padraoId, Long materialId) {
        boolean removed = compMaterialRepository.deleteByPadrao_IdAndCompositor_Material_Id(padraoId, materialId);
        if (!removed) {
            throw new ResourceNotFoundException(
                    "Nenhuma composição de material encontrada para o padrão "
                            + padraoId + " e material " + materialId
            );
        }
    }

    public List<ResPadraoDTO> findPadraoByCompositor(Long id, LoadCatalogParamsDTO params, CompositorEnum compType) {
        if (compType.equals(CompositorEnum.AMBIENTE)) return findPadraoByAmbiente(id, params);
        if (compType.equals(CompositorEnum.MATERIAL)) return findPadraoByMaterial(id, params);

        throw new InvalidCompositorException("Tipo de compositor inválido: " + compType);
    }

    public List<ItemAmbiente> findItensAmbienteByPadrao(Long padraoId, Long ambienteId, Long itemId) {
        if (ambienteId == null && itemId == null) {
            return compAmbienteRepository.findByPadrao_Id(padraoId)
                    .stream()
                    .map(ComposicaoAmbiente::getCompositor)
                    .toList();
        }

        if (ambienteId != null && itemId == null) {
            return compAmbienteRepository.findFilteredByAmbiente(padraoId, ambienteId)
                    .stream()
                    .map(ComposicaoAmbiente::getCompositor)
                    .toList();
        }

        if (ambienteId == null) {
            return compAmbienteRepository.findFilteredByItem(padraoId, itemId)
                    .stream()
                    .map(ComposicaoAmbiente::getCompositor)
                    .toList();
        }

        return compAmbienteRepository.findFiltered(padraoId, ambienteId, itemId)
                .stream()
                .map(ComposicaoAmbiente::getCompositor)
                .toList();
    }

    public List<MarcaMaterial> findMarcasMaterialByPadrao(Long padraoId, Long materialId, Long marcaId) {
        if (materialId == null && marcaId == null) {
            return compMaterialRepository.findByPadrao_Id(padraoId)
                    .stream()
                    .map(ComposicaoMaterial::getCompositor)
                    .toList();
        }

        if (materialId != null && marcaId == null) {
            return compMaterialRepository.findFilteredByMaterial(padraoId, materialId)
                    .stream()
                    .map(ComposicaoMaterial::getCompositor)
                    .toList();
        }

        if (materialId == null) {
            return compMaterialRepository.findFilteredByMarca(padraoId, marcaId)
                    .stream()
                    .map(ComposicaoMaterial::getCompositor)
                    .toList();
        }

        return compMaterialRepository.findFiltered(padraoId, materialId, marcaId)
                .stream()
                .map(ComposicaoMaterial::getCompositor)
                .toList();
    }

    public ResComposicaoDTO addSingleAssociationToPadrao(Long id, Long associationId, CompositorEnum compType) {
        if (compType.equals(CompositorEnum.AMBIENTE)) return addAmbienteRelToPadrao(id, associationId);
        if (compType.equals(CompositorEnum.MATERIAL)) return addMaterialRelToPadrao(id, associationId);

        throw new InvalidCompositorException("Tipo de compositor inválido: " + compType);
    }

    public List<ResComposicaoDTO> addAllAssociationsToPadrao(Long id, Long associationId, CompositorEnum compType) {
        if (compType.equals(CompositorEnum.AMBIENTE)) return addAllAmbienteRelToPadrao(id, associationId);
        if (compType.equals(CompositorEnum.MATERIAL)) return addAllMaterialRelToPadrao(id, associationId);

        throw new InvalidCompositorException("Tipo de compositor inválido: " + compType);
    }

    public void removeCompositorFromPadrao(Long padraoId, Long compositorId, CompositorEnum compType) {
        if (compType.equals(CompositorEnum.AMBIENTE)) removeAllAmbienteAssociationsFromPadrao(padraoId, compositorId);
        if (compType.equals(CompositorEnum.MATERIAL)) removeAllMaterialAssociationsFromPadrao(padraoId, compositorId);

        throw new InvalidCompositorException("Tipo de compositor inválido: " + compType);
    }

    public void removeSingleCompFromPadrao(Long compId, CompositorEnum compType) {
        if (compType.equals(CompositorEnum.AMBIENTE)) {
            compAmbienteRepository.deleteById(compId);
        } else if (compType.equals(CompositorEnum.MATERIAL)) {
            compMaterialRepository.deleteById(compId);
        } else {
            throw new InvalidCompositorException("Nenhuma Composição encontrado para padrão " + compId);
        }
    }
}
