package com.kaipai.service.actor;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.dto.*;
import java.util.List;
public interface ActorWorkService {
    PageResult<ActorWorkRespDTO> listWorks(Long userId, ActorWorkQueryDTO query);
    ActorWorkRespDTO createWork(Long userId, ActorWorkSaveDTO request);
    ActorWorkRespDTO work(Long userId, Long id);
    ActorWorkRespDTO updateWork(Long userId, Long id, ActorWorkSaveDTO request);
    void deleteWork(Long userId, Long id);
    List<ActorWorkRespDTO> replaceRepresentativeWorks(Long userId, ActorRepresentativeWorksUpdateDTO request);
}
