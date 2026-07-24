package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.dto.*;
import com.kaipai.service.actor.ActorMediaAssetService;
import com.kaipai.service.actor.ActorWorkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/actor/works")
@RequiredArgsConstructor
public class ActorWorkController {
    private final ActorWorkService actorWorkService;
    private final ActorMediaAssetService actorMediaAssetService;

    @GetMapping public R<PageResult<ActorWorkRespDTO>> list(Authentication auth, @ModelAttribute ActorWorkQueryDTO query) { return R.ok(actorWorkService.listWorks(userId(auth), query)); }
    @PostMapping public R<ActorWorkRespDTO> create(Authentication auth, @Valid @RequestBody ActorWorkSaveDTO dto) { return R.ok(actorWorkService.createWork(userId(auth), dto)); }
    @GetMapping("/{id}") public R<ActorWorkRespDTO> get(Authentication auth, @PathVariable Long id) { return R.ok(actorWorkService.work(userId(auth), id)); }
    @PutMapping("/{id}") public R<ActorWorkRespDTO> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ActorWorkSaveDTO dto) { return R.ok(actorWorkService.updateWork(userId(auth), id, dto)); }
    @DeleteMapping("/{id}") public R<Void> delete(Authentication auth, @PathVariable Long id) { actorWorkService.deleteWork(userId(auth), id); return R.ok(); }
    @PutMapping("/{id}/assets") public R<Void> replaceAssets(Authentication auth, @PathVariable Long id, @Valid @RequestBody ActorWorkAssetsReplaceDTO dto) { actorMediaAssetService.replaceWorkAssets(userId(auth), id, dto); return R.ok(); }
    @GetMapping("/representatives") public R<List<ActorWorkRespDTO>> representatives(Authentication auth) { return R.ok(actorWorkService.representativeWorks(userId(auth))); }
    @PutMapping("/representatives") public R<List<ActorWorkRespDTO>> representatives(Authentication auth, @Valid @RequestBody ActorRepresentativeWorksUpdateDTO dto) { return R.ok(actorWorkService.replaceRepresentativeWorks(userId(auth), dto)); }

    private Long userId(Authentication auth) { if (auth == null || !(auth.getPrincipal() instanceof Long id)) throw new BizException("未登录或登录态失效"); return id; }
}
