package com.tikectsystem.exception;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.enums.BaseCode;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 业务异常
 * @author: 阿星不是程序员
 **/
@Data
public class TikectsystemFrameException extends BaseException {

	private Integer code;
	
	private String message;

	public TikectsystemFrameException() {
		super();
	}

	public TikectsystemFrameException(String message) {
		super(message);
	}
	
	
	public TikectsystemFrameException(String code, String message) {
		super(message);
		this.code = Integer.parseInt(code);
		this.message = message;
	}
	
	public TikectsystemFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}
	
	public TikectsystemFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}
	
	public TikectsystemFrameException(ApiResponse apiResponse) {
		super(apiResponse.getMessage());
		this.code = apiResponse.getCode();
		this.message = apiResponse.getMessage();
	}

	public TikectsystemFrameException(Throwable cause) {
		super(cause);
	}

	public TikectsystemFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}

	public TikectsystemFrameException(Integer code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.message = message;
	}
}
