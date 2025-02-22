package io.dataease.service.chart;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.dataease.plugins.common.base.domain.ChartViewCacheExample;
import io.dataease.plugins.common.base.domain.ChartViewCacheWithBLOBs;
import io.dataease.plugins.common.base.domain.ChartViewExample;
import io.dataease.plugins.common.base.domain.ChartViewWithBLOBs;
import io.dataease.plugins.common.base.mapper.ChartViewCacheMapper;
import io.dataease.plugins.common.base.mapper.ChartViewMapper;
import io.dataease.plugins.common.dto.chart.ChartCustomFilterItemDTO;
import io.dataease.plugins.common.dto.chart.ChartFieldCustomFilterDTO;
import io.dataease.plugins.common.request.chart.filter.FilterTreeItem;
import io.dataease.plugins.common.request.chart.filter.FilterTreeObj;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author Junjun
 */
@Service
public class ChartViewOldDataMergeService {

    private final static Gson gson = new Gson();

    @Resource
    private ChartViewMapper chartViewMapper;
    @Resource
    private ChartViewCacheMapper chartViewCacheMapper;

    /**
     * 视图过滤器重构，合并老数据，将list变成tree
     */
    public void mergeOldData() {
        // 获取所有视图数据
        // 把一个视图中的过滤器，即customFilter字段进行重构
        // 之前是以list形式储存，一个字段是一个item
        // 现在把一个字段当做tree中的一个节点
        // 节点中如果是logic且length>1，保留and或or，每一条都变成一个子节点；如果是枚举或只有1条，则当做and处理并保留值
        // 最后把字段之间通过and的逻辑合并
        List<ChartViewWithBLOBs> chartViewWithBLOBs = chartViewMapper.selectByExampleWithBLOBs(new ChartViewExample());
        if (CollectionUtils.isEmpty(chartViewWithBLOBs)) {
            return;
        }

        for (ChartViewWithBLOBs view : chartViewWithBLOBs) {
            Type filterTokenType = new TypeToken<List<ChartFieldCustomFilterDTO>>() {
            }.getType();
            List<ChartFieldCustomFilterDTO> fieldCustomFilter;
            // 尝试将历史数据转成list，如果转换出现异常，则忽略该视图继续执行下一个
            try {
                fieldCustomFilter = gson.fromJson(view.getCustomFilter(), filterTokenType);
            } catch (Exception e) {
                continue;
            }

            if (CollectionUtils.isEmpty(fieldCustomFilter)) {
                // 将 '[]' 转换成 '{}'
                view.setCustomFilter("{}");
            } else {
                FilterTreeObj tree = new FilterTreeObj();
                tree.setItems(new ArrayList<>());
                if (fieldCustomFilter.size() == 1) {
                    ChartFieldCustomFilterDTO filterDTO = fieldCustomFilter.get(0);
                    tree.setLogic(filterDTO.getLogic());
                    if (StringUtils.equalsIgnoreCase(filterDTO.getFilterType(), "enum")) {
                        FilterTreeItem item = new FilterTreeItem();
                        item.setType("item");
                        item.setFieldId(filterDTO.getId());
                        item.setFilterType(filterDTO.getFilterType());
                        item.setEnumValue(filterDTO.getEnumCheckField());
                        tree.getItems().add(item);
                    } else {
                        List<ChartCustomFilterItemDTO> filter = filterDTO.getFilter();
                        if (CollectionUtils.isNotEmpty(filter)) {
                            for (ChartCustomFilterItemDTO f : filter) {
                                FilterTreeItem item = new FilterTreeItem();
                                item.setType("item");
                                item.setFieldId(filterDTO.getId());
                                item.setFilterType(filterDTO.getFilterType());
                                item.setTerm(f.getTerm());
                                item.setValue(f.getValue());
                                item.setEnumValue(new ArrayList<>());
                                tree.getItems().add(item);
                            }
                        }
                    }
                } else {
                    tree.setLogic("and");
                    for (ChartFieldCustomFilterDTO dto : fieldCustomFilter) {
                        if (StringUtils.equalsIgnoreCase(dto.getFilterType(), "enum")) {
                            FilterTreeItem item = new FilterTreeItem();
                            item.setType("item");
                            item.setFieldId(dto.getId());
                            item.setFilterType(dto.getFilterType());
                            item.setEnumValue(dto.getEnumCheckField());
                            tree.getItems().add(item);
                        } else {
                            List<ChartCustomFilterItemDTO> filter = dto.getFilter();
                            if (CollectionUtils.isNotEmpty(filter)) {
                                if (filter.size() == 1) {
                                    ChartCustomFilterItemDTO f = filter.get(0);
                                    FilterTreeItem item = new FilterTreeItem();
                                    item.setType("item");
                                    item.setFieldId(dto.getId());
                                    item.setFilterType(dto.getFilterType());
                                    item.setTerm(f.getTerm());
                                    item.setValue(f.getValue());
                                    item.setEnumValue(new ArrayList<>());
                                    tree.getItems().add(item);
                                } else {
                                    FilterTreeItem item = new FilterTreeItem();
                                    item.setType("tree");
                                    item.setEnumValue(new ArrayList<>());
                                    FilterTreeObj subTree = new FilterTreeObj();
                                    subTree.setLogic(dto.getLogic());
                                    subTree.setItems(new ArrayList<>());
                                    for (ChartCustomFilterItemDTO f : filter) {
                                        FilterTreeItem itemTree = new FilterTreeItem();
                                        itemTree.setType("item");
                                        itemTree.setFieldId(dto.getId());
                                        itemTree.setFilterType(dto.getFilterType());
                                        itemTree.setTerm(f.getTerm());
                                        itemTree.setValue(f.getValue());
                                        itemTree.setEnumValue(new ArrayList<>());
                                        subTree.getItems().add(itemTree);
                                    }
                                    item.setSubTree(subTree);
                                    tree.getItems().add(item);
                                }
                            }
                        }
                    }
                }
                view.setCustomFilter(gson.toJson(tree));
            }

            try {
                chartViewMapper.updateByPrimaryKeyWithBLOBs(view);
            } catch (Exception e) {
                // do nothing,to continue
                e.printStackTrace();
            }
        }
    }

    public void mergeCacheOldData() {
        // 获取所有视图数据
        // 把一个视图中的过滤器，即customFilter字段进行重构
        // 之前是以list形式储存，一个字段是一个item
        // 现在把一个字段当做tree中的一个节点
        // 节点中如果是logic且length>1，保留and或or，每一条都变成一个子节点；如果是枚举或只有1条，则当做and处理并保留值
        // 最后把字段之间通过and的逻辑合并
        List<ChartViewCacheWithBLOBs> chartViewWithBLOBs = chartViewCacheMapper.selectByExampleWithBLOBs(new ChartViewCacheExample());
        if (CollectionUtils.isEmpty(chartViewWithBLOBs)) {
            return;
        }

        for (ChartViewCacheWithBLOBs view : chartViewWithBLOBs) {
            Type filterTokenType = new TypeToken<List<ChartFieldCustomFilterDTO>>() {
            }.getType();

            List<ChartFieldCustomFilterDTO> fieldCustomFilter;
            // 尝试将历史数据转成list，如果转换出现异常，则忽略该视图继续执行下一个
            try {
                fieldCustomFilter = gson.fromJson(view.getCustomFilter(), filterTokenType);
            } catch (Exception e) {
                continue;
            }

            if (CollectionUtils.isEmpty(fieldCustomFilter)) {
                // 将 '[]' 转换成 '{}'
                view.setCustomFilter("{}");
            } else {
                FilterTreeObj tree = new FilterTreeObj();
                tree.setItems(new ArrayList<>());
                if (fieldCustomFilter.size() == 1) {
                    ChartFieldCustomFilterDTO filterDTO = fieldCustomFilter.get(0);
                    tree.setLogic(filterDTO.getLogic());
                    if (StringUtils.equalsIgnoreCase(filterDTO.getFilterType(), "enum")) {
                        FilterTreeItem item = new FilterTreeItem();
                        item.setType("item");
                        item.setFieldId(filterDTO.getId());
                        item.setFilterType(filterDTO.getFilterType());
                        item.setEnumValue(filterDTO.getEnumCheckField());
                        tree.getItems().add(item);
                    } else {
                        List<ChartCustomFilterItemDTO> filter = filterDTO.getFilter();
                        if (CollectionUtils.isNotEmpty(filter)) {
                            for (ChartCustomFilterItemDTO f : filter) {
                                FilterTreeItem item = new FilterTreeItem();
                                item.setType("item");
                                item.setFieldId(filterDTO.getId());
                                item.setFilterType(filterDTO.getFilterType());
                                item.setTerm(f.getTerm());
                                item.setValue(f.getValue());
                                item.setEnumValue(new ArrayList<>());
                                tree.getItems().add(item);
                            }
                        }
                    }
                } else {
                    tree.setLogic("and");
                    for (ChartFieldCustomFilterDTO dto : fieldCustomFilter) {
                        if (StringUtils.equalsIgnoreCase(dto.getFilterType(), "enum")) {
                            FilterTreeItem item = new FilterTreeItem();
                            item.setType("item");
                            item.setFieldId(dto.getId());
                            item.setFilterType(dto.getFilterType());
                            item.setEnumValue(dto.getEnumCheckField());
                            tree.getItems().add(item);
                        } else {
                            List<ChartCustomFilterItemDTO> filter = dto.getFilter();
                            if (CollectionUtils.isNotEmpty(filter)) {
                                if (filter.size() == 1) {
                                    ChartCustomFilterItemDTO f = filter.get(0);
                                    FilterTreeItem item = new FilterTreeItem();
                                    item.setType("item");
                                    item.setFieldId(dto.getId());
                                    item.setFilterType(dto.getFilterType());
                                    item.setTerm(f.getTerm());
                                    item.setValue(f.getValue());
                                    item.setEnumValue(new ArrayList<>());
                                    tree.getItems().add(item);
                                } else {
                                    FilterTreeItem item = new FilterTreeItem();
                                    item.setType("tree");
                                    item.setEnumValue(new ArrayList<>());
                                    FilterTreeObj subTree = new FilterTreeObj();
                                    subTree.setLogic(dto.getLogic());
                                    subTree.setItems(new ArrayList<>());
                                    for (ChartCustomFilterItemDTO f : filter) {
                                        FilterTreeItem itemTree = new FilterTreeItem();
                                        itemTree.setType("item");
                                        itemTree.setFieldId(dto.getId());
                                        itemTree.setFilterType(dto.getFilterType());
                                        itemTree.setTerm(f.getTerm());
                                        itemTree.setValue(f.getValue());
                                        itemTree.setEnumValue(new ArrayList<>());
                                        subTree.getItems().add(itemTree);
                                    }
                                    item.setSubTree(subTree);
                                    tree.getItems().add(item);
                                }
                            }
                        }
                    }
                }
                view.setCustomFilter(gson.toJson(tree));
            }

            try {
                chartViewCacheMapper.updateByPrimaryKeyWithBLOBs(view);
            } catch (Exception e) {
                // do nothing,to continue
                e.printStackTrace();
            }
        }
    }
}
