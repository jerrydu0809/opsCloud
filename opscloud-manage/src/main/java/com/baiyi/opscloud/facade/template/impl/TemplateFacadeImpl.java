package com.baiyi.opscloud.facade.template.impl;

import com.baiyi.opscloud.common.datasource.KubernetesConfig;
import com.baiyi.opscloud.common.exception.common.CommonRuntimeException;
import com.baiyi.opscloud.common.template.YamlUtil;
import com.baiyi.opscloud.common.template.YamlVars;
import com.baiyi.opscloud.common.util.BeanCopierUtil;
import com.baiyi.opscloud.core.factory.DsConfigHelper;
import com.baiyi.opscloud.domain.DataTable;
import com.baiyi.opscloud.domain.generator.opscloud.BusinessTemplate;
import com.baiyi.opscloud.domain.generator.opscloud.DatasourceConfig;
import com.baiyi.opscloud.domain.generator.opscloud.Env;
import com.baiyi.opscloud.domain.generator.opscloud.Template;
import com.baiyi.opscloud.domain.param.SimpleExtend;
import com.baiyi.opscloud.domain.param.template.BusinessTemplateParam;
import com.baiyi.opscloud.domain.param.template.TemplateParam;
import com.baiyi.opscloud.domain.vo.template.BusinessTemplateVO;
import com.baiyi.opscloud.domain.vo.template.TemplateVO;
import com.baiyi.opscloud.facade.datasource.DsInstanceFacade;
import com.baiyi.opscloud.facade.template.TemplateFacade;
import com.baiyi.opscloud.facade.template.factory.ITemplateConsume;
import com.baiyi.opscloud.facade.template.factory.TemplateFactory;
import com.baiyi.opscloud.packer.datasource.DsInstancePacker;
import com.baiyi.opscloud.packer.template.BusinessTemplatePacker;
import com.baiyi.opscloud.packer.template.TemplatePacker;
import com.baiyi.opscloud.service.datasource.DsInstanceService;
import com.baiyi.opscloud.service.sys.EnvService;
import com.baiyi.opscloud.service.template.BusinessTemplateService;
import com.baiyi.opscloud.service.template.TemplateService;
import com.google.common.base.Joiner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @Author baiyi
 * @Date 2021/12/6 10:58 AM
 * @Version 1.0
 */
@Service
@RequiredArgsConstructor
public class TemplateFacadeImpl implements TemplateFacade {

    private final BusinessTemplateService businessTemplateService;

    private final TemplateService templateService;

    private final BusinessTemplatePacker businessTemplatePacker;

    private final TemplatePacker templatePacker;

    private final DsInstanceService dsInstanceService;

    private final DsInstancePacker dsInstancePacker;

    private final DsConfigHelper dsConfigHelper;

    private final EnvService envService;

    private final DsInstanceFacade dsInstanceFacade;


    @Override
    public DataTable<TemplateVO.Template> queryTemplatePage(TemplateParam.TemplatePageQuery pageQuery) {
        DataTable<Template> table = templateService.queryPageByParam(pageQuery);
        return new DataTable<>(templatePacker.wrapVOList(table.getData(), pageQuery), table.getTotalNum());
    }

    @Override
    public DataTable<BusinessTemplateVO.BusinessTemplate> queryBusinessTemplatePage(BusinessTemplateParam.BusinessTemplatePageQuery pageQuery) {
        DataTable<BusinessTemplate> table = businessTemplateService.queryPageByParam(pageQuery);
        return new DataTable<>(businessTemplatePacker.wrapVOList(table.getData(), pageQuery), table.getTotalNum());
    }

    private YamlVars.Vars toVars(BusinessTemplate bizTemplate) {
        YamlVars.Vars vars = YamlUtil.toVars(bizTemplate.getVars());
        if (!vars.getVars().containsKey("envName")) {
            Env env = envService.getByEnvType(bizTemplate.getEnvType());
            vars.getVars().put("envName", env.getEnvName());
        }
        return vars;
    }

    @Override
    public BusinessTemplateVO.BusinessTemplate createAssetByBusinessTemplate(int id) {
        BusinessTemplate bizTemplate = businessTemplateService.getById(id);
        if (bizTemplate == null)
            throw new CommonRuntimeException("无法创建资产: 业务模板不存在!");
        if (StringUtils.isEmpty(bizTemplate.getName()))
            throw new CommonRuntimeException("无法创建资产: 业务模板名称不合规（空值）!");
        Template template = templateService.getById(bizTemplate.getTemplateId());
        if (template == null)
            throw new CommonRuntimeException("无法创建资产: 模板不存在!");
        ITemplateConsume iTemplateConsume = TemplateFactory.getByInstanceAsset(template.getInstanceType(), template.getTemplateKey());
        if (iTemplateConsume == null) {
            throw new CommonRuntimeException("无法创建资产: 无可用的生产者!");
        }
        return iTemplateConsume.produce(bizTemplate);
    }

    @Override
    public void deleteBusinessTemplateById(int id) {
        businessTemplateService.deleteById(id);
    }

    @Override
    public BusinessTemplateVO.BusinessTemplate addBusinessTemplate(BusinessTemplateParam.BusinessTemplate addBusinessTemplate) {
        BusinessTemplate bizTemplate = BeanCopierUtil.copyProperties(addBusinessTemplate, BusinessTemplate.class);
        Template template = templateService.getById(bizTemplate.getTemplateId());
        if (template == null)
            throw new CommonRuntimeException("无法创建业务模板: 模板不存在!");
        bizTemplate.setEnvType(template.getEnvType());
        bizTemplate.setVars(template.getVars());
        setName(bizTemplate);
        businessTemplateService.add(bizTemplate);
        return businessTemplatePacker.wrapVO(bizTemplate, SimpleExtend.EXTEND);
    }

    @Override
    public BusinessTemplateVO.BusinessTemplate updateBusinessTemplate(BusinessTemplateParam.BusinessTemplate businessTemplate) {
        BusinessTemplate preBizTemplate = businessTemplateService.getById(businessTemplate.getId());
        // 用户修改模版
        if (!preBizTemplate.getTemplateId().equals(businessTemplate.getTemplateId())) {
            Template template = templateService.getById(businessTemplate.getTemplateId());
            if (template == null)
                throw new CommonRuntimeException("无法创建业务模板: 模板不存在!");
            preBizTemplate.setTemplateId(businessTemplate.getTemplateId());
            preBizTemplate.setEnvType(template.getEnvType());
            preBizTemplate.setVars(template.getVars());
        } else {
            preBizTemplate.setVars(businessTemplate.getVars());
        }
        if (StringUtils.isEmpty(businessTemplate.getName())) {
            preBizTemplate.setName("");
            setName(preBizTemplate);
        }
        businessTemplateService.update(preBizTemplate);
        return businessTemplatePacker.wrapVO(preBizTemplate, SimpleExtend.EXTEND);
    }

    private void setName(BusinessTemplate bizTemplate) {
        try {
            if (StringUtils.isEmpty(bizTemplate.getName())) {
                Template template = templateService.getById(bizTemplate.getTemplateId());
                Env env = envService.getByEnvType(bizTemplate.getEnvType());
                YamlVars.Vars vars = YamlUtil.toVars(bizTemplate.getVars());
                DatasourceConfig dsConfig = dsConfigHelper.getConfigByInstanceUuid(bizTemplate.getInstanceUuid());
                KubernetesConfig.Kubernetes config = dsConfigHelper.build(dsConfig, KubernetesConfig.class).getKubernetes();
                KubernetesConfig.Nomenclature nomenclature;
                if ("DEPLOYMENT".equals(template.getTemplateKey())) {
                    setName(bizTemplate, config.getDeployment().getNomenclature(), vars, env);
                    return;
                }
                if ("SERVICE".equals(template.getTemplateKey())) {
                    setName(bizTemplate, config.getService().getNomenclature(), vars, env);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setName(BusinessTemplate bizTemplate, KubernetesConfig.Nomenclature nomenclature, YamlVars.Vars vars, Env env) {
        if (!vars.getVars().containsKey("appName")) return;
        String name = Joiner.on("").skipNulls().join(nomenclature.getPrefix(),
                vars.getVars().get("appName"),
                nomenclature.getSuffix(), "-", env.getEnvName()
        );
        bizTemplate.setName(name);
    }
}