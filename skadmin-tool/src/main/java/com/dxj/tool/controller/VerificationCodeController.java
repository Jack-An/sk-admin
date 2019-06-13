package com.dxj.tool.controller;

import com.dxj.tool.domain.VerificationCode;
import com.dxj.tool.domain.vo.EmailVo;
import com.dxj.tool.service.EmailService;
import com.dxj.tool.service.VerificationCodeService;
import com.dxj.common.util.SkAdminConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author dxj
 * @date 2019-04-26
 */
@RestController
@RequestMapping("api")
public class VerificationCodeController {

    private final VerificationCodeService verificationCodeService;

    private final EmailService emailService;

    @Autowired
    public VerificationCodeController(VerificationCodeService verificationCodeService, EmailService emailService) {
        this.verificationCodeService = verificationCodeService;
        this.emailService = emailService;
    }

    @PostMapping(value = "/code/resetEmail")
    public ResponseEntity<Void> resetEmail(@RequestBody VerificationCode code) {
        sendEmail(code);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void sendEmail(@RequestBody VerificationCode code) {
        code.setScenes(SkAdminConstant.RESET_MAIL);
        EmailVo emailVo = verificationCodeService.sendEmail(code);
        emailService.send(emailVo, emailService.find());
    }

    @PostMapping(value = "/code/email/resetPass")
    public ResponseEntity<Void> resetPass(@RequestParam String email) {
        VerificationCode code = new VerificationCode();
        code.setType("email");
        code.setValue(email);
        sendEmail(code);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping(value = "/code/validated")
    public ResponseEntity<Void> validated(VerificationCode code){
        verificationCodeService.validated(code);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}