package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.util.List;

/**
 * 演员卡参演作品快照响应 DTO。
 *
 * <p>{@code stills} 在出口处由 {@code stills_json} 反序列化为数组，
 * 使前端拿到的是结构化列表而非 JSON 字符串 —— 与 photosJson 那类
 * 「前端自行 JSON.parse」的做法相反，后者已在本 Spec 记录为缺陷成因
 * （computed 内解析无保护会打挂整页，见 requirements §3.7）。
 */
@Data
public class ActorCardWorkRespDTO {

    private Long id;

    /** 来源演艺经历 id，新增作品时为 null */
    private Long sourceWorkId;

    private String workTitle;

    /** 作品类型: short_drama|micro_film|tv|movie|other */
    private String workType;

    private String roleName;

    /** 剧照 URL 列表，第一张为封面 */
    private List<String> stills;

    private Integer sortOrder;
}
