package org.gable.blendata.nextmove.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.service.config.AppConfig;
import org.gable.blendata.nextmove.service.service.NoneCtrlService;
import org.gable.blendata.nextmove.service.service.ZeroCtrlService;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.ServiceConst.ServiceId;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.dto.StopTaskRequestWrapper;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/manage-file")
@RequiredArgsConstructor
public class ManageFileController {


    private final AppConfig appConfig;
    private final ZeroCtrlService zeroCtrlService;
    private final NoneCtrlService noneCtrlService;

    @PostMapping("/transfer")
    public String transferFile(@RequestBody  TransferRequestWrapper transferRequestWrapper){
        if(log.isDebugEnabled()) {
            for (String filePath : transferRequestWrapper.getFilePathStrs()) {
                log.debug("{}[Transfer] file {}", AppConst.PREFIX_LOG, filePath);
            }
        }

        if(ServiceId.ZERO_CONTROL.val().equalsIgnoreCase(appConfig.getServiceId())){
            zeroCtrlService.moveFiles(transferRequestWrapper);
        }else if(ServiceId.NONE_CONTROL.val().equalsIgnoreCase(appConfig.getServiceId())){
            noneCtrlService.moveFiles(transferRequestWrapper);
        }
        return "Success";
    }

    @PostMapping("/transfer/stop")
    public String stopTransferFile(@RequestBody List<StopTaskRequestWrapper> stopTaskRequestWrappers) {
        if(log.isDebugEnabled()) {
            for (StopTaskRequestWrapper stopTaskRequestWrapper : stopTaskRequestWrappers) {
                log.debug("{}[Stop Transfer] client id =  {}, task id = {} "
                        , AppConst.PREFIX_LOG
                        , stopTaskRequestWrapper.getClientId()
                        , stopTaskRequestWrapper.getTaskId());
            }
        }
        if(ServiceId.ZERO_CONTROL.val().equalsIgnoreCase(appConfig.getServiceId())){
            zeroCtrlService.stopMoveFiles(stopTaskRequestWrappers);
        }else if(ServiceId.NONE_CONTROL.val().equalsIgnoreCase(appConfig.getServiceId())){
            noneCtrlService.stopMoveFiles(stopTaskRequestWrappers);
        }
        return "Success";
    }
}
