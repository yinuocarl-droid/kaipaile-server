-- V20260731_002: 演员卡背景图库初始测试数据 (00-206)
-- 使用现有 COS 上的 AI 生成图作为占位背景，生产环境替换为专业设计背景图

INSERT INTO `actor_card_background` (`style`, `image_url`, `thumbnail_url`, `sort_order`, `enabled`) VALUES

-- 经典风格 (classic)
('classic', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/c1e789d8937847c8a71f08a94ae10f63.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/c1e789d8937847c8a71f08a94ae10f63.png', 1, 1),
('classic', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/94f0fb3e924f4ecab8982a805923a92f.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/94f0fb3e924f4ecab8982a805923a92f.png', 2, 1),
('classic', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/056025a663144e64bd311f6ea4458530.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/056025a663144e64bd311f6ea4458530.png', 3, 1),

-- 都市风格 (urban)
('urban', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/2b6da833a950402892b305bea345fc41.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/2b6da833a950402892b305bea345fc41.png', 1, 1),
('urban', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/3afdcccf2d704cad9d1bf54758196068.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/3afdcccf2d704cad9d1bf54758196068.png', 2, 1),
('urban', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/19d282a8cdc14c07beed82e3ff497e14.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/19d282a8cdc14c07beed82e3ff497e14.png', 3, 1),

-- 古风风格 (ancient)
('ancient', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/fa1a8bd121964458a4d315ae3afe3f58.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/fa1a8bd121964458a4d315ae3afe3f58.png', 1, 1),
('ancient', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/c1e789d8937847c8a71f08a94ae10f63.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/c1e789d8937847c8a71f08a94ae10f63.png', 2, 1),

-- 清新风格 (fresh)
('fresh', 'https://kaipai-1412601014.cos.ap-shanghai.myqcloud.com/avatar/2026/05/08/e5d9a10d87954527bb554337ac2f286b.png',
 'https://kaipai-1412601014.cos.ap-shanghai.myqcloud.com/avatar/2026/05/08/e5d9a10d87954527bb554337ac2f286b.png', 1, 1),
('fresh', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/94f0fb3e924f4ecab8982a805923a92f.png',
 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/94f0fb3e924f4ecab8982a805923a92f.png', 2, 1);
