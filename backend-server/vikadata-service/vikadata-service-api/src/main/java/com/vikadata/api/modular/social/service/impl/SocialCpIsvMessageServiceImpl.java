package com.vikadata.api.modular.social.service.impl;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Maps;
import me.chanjar.weixin.common.error.WxErrorException;

import com.vikadata.api.modular.social.enums.SocialCpIsvMessageProcessStatus;
import com.vikadata.api.modular.social.mapper.SocialCpIsvMessageMapper;
import com.vikadata.api.modular.social.service.ISocialCpIsvEntityHandler;
import com.vikadata.api.modular.social.service.ISocialCpIsvMessageService;
import com.vikadata.entity.SocialCpIsvMessageEntity;
import com.vikadata.integration.rabbitmq.RabbitSenderService;
import com.vikadata.social.wecom.constants.WeComIsvMessageType;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import static com.vikadata.api.config.rabbitmq.TopicRabbitMqConfig.SOCIAL_ISV_EVENT_EXCHANGE;
import static com.vikadata.api.config.rabbitmq.TopicRabbitMqConfig.SOCIAL_ISV_WECOM_ROUTING_KEY;

/**
 * <p>
 * 第三方平台集成 - 企业微信第三方服务商应用消息通知信息
 * </p>
 *
 * @author 刘斌华
 * @date 2022-01-05 17:01:04
 */
@Service
public class SocialCpIsvMessageServiceImpl extends ServiceImpl<SocialCpIsvMessageMapper, SocialCpIsvMessageEntity>
        implements ISocialCpIsvMessageService, InitializingBean {

    private static final Map<WeComIsvMessageType, ISocialCpIsvEntityHandler> ENTITY_HANDLERS = Maps.newHashMapWithExpectedSize(16);

    private static final String SQL_LIMIT = "LIMIT %d";

    @Resource
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private RabbitSenderService rabbitSenderService;

    @Override
    public List<SocialCpIsvMessageEntity> getUnprocessedList(int size) {
        LambdaQueryWrapper<SocialCpIsvMessageEntity> queryWrapper = Wrappers.lambdaQuery(SocialCpIsvMessageEntity.class)
                .between(SocialCpIsvMessageEntity::getProcessStatus, SocialCpIsvMessageProcessStatus.PENDING.getValue(),
                        SocialCpIsvMessageProcessStatus.REJECT_TEMPORARILY.getValue())
                .orderByAsc(SocialCpIsvMessageEntity::getTimestamp)
                .last(String.format(SQL_LIMIT, size));
        return list(queryWrapper);
    }

    @Override
    public void doUnprocessedInfo(SocialCpIsvMessageEntity unprocessedInfo) throws WxErrorException {
        ENTITY_HANDLERS.get(WeComIsvMessageType.fromType(unprocessedInfo.getType())).process(unprocessedInfo);
    }

    @Override
    public void sendToMq(Long unprocessedId, String infoType, String authCorpId, String suiteId) {
        rabbitSenderService.topicSend(SOCIAL_ISV_EVENT_EXCHANGE, SOCIAL_ISV_WECOM_ROUTING_KEY,
                unprocessedId.toString(), SocialCpIsvMessageEntity.builder()
                        .id(unprocessedId)
                        .suiteId(suiteId)
                        .infoType(infoType)
                        .authCorpId(authCorpId)
                        .build());
    }

    @Override
    public void updateStatusById(Long id, SocialCpIsvMessageProcessStatus status) {
        baseMapper.updateStatusById(id, status.getValue());
    }

    @Override
    public void afterPropertiesSet() {
        applicationContext.getBeansOfType(ISocialCpIsvEntityHandler.class).values().
                forEach(handler -> ENTITY_HANDLERS.put(handler.type(), handler));
    }

}
