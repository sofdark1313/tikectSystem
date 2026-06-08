import mitt from 'mitt';

export function removeLegacyBrandText(value) {
    if (typeof value !== 'string') {
        return value;
    }
    return value
        .replace(new RegExp('\u5927\u9ea6\u7f51', 'g'), '')
        .replace(new RegExp('\u5927\u9ea6', 'g'), '')
        .replace(new RegExp('\u4e70\u7968\u4e0a', 'g'), '在线购票')
        .replace(new RegExp('d' + 'amai', 'gi'), 'ticket');
}

const LEGACY_BRAND_DISPLAY_KEYS = new Set([
    'name',
    'title',
    'actor',
    'place',
    'areaName',
    'categoryName',
    'showWeekTime',
    'introduce',
    'value',
    'message',
    'preSellInstruction',
    'importantNotice',
    'purchaseLimitRule',
    'refundTicketRule',
    'entryRule',
    'childPurchase',
    'invoiceSpecification',
    'realTicketPurchaseRule',
    'abnormalOrderDescription',
    'performanceDuration',
    'entryTime',
    'minPerformanceCount',
    'mainActor',
    'minPerformanceDuration',
    'prohibitedItem',
    'depositSpecification',
    'refundExplain',
    'relNameTicketEntranceExplain',
    'chooseSeatExplain',
    'electronicDeliveryTicketExplain',
    'electronicInvoiceExplain',
    'detail'
]);

export function removeLegacyBrandFromData(data, key = '') {
    if (Array.isArray(data)) {
        return data.map(item => removeLegacyBrandFromData(item, key));
    }
    if (data && typeof data === 'object') {
        Object.keys(data).forEach(childKey => {
            data[childKey] = removeLegacyBrandFromData(data[childKey], childKey);
        });
        return data;
    }
    return LEGACY_BRAND_DISPLAY_KEYS.has(key) ? removeLegacyBrandText(data) : data;
}

export function getCurrentDateTime() {
    const now = new Date();
    const year = now.getFullYear();
    const month = (now.getMonth() + 1).toString().padStart(2, '0');
    const day = now.getDate().toString().padStart(2, '0');
    const hours = now.getHours().toString().padStart(2, '0');
    const minutes = now.getMinutes().toString().padStart(2, '0');
    const seconds = now.getSeconds().toString().padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

export function getCurrentDate (dateStr) {
    const now = new Date(dateStr);
    const year = now.getFullYear();
    const month = (now.getMonth() + 1).toString().padStart(2, '0');
    const day = now.getDate().toString().padStart(2, '0');
    const hours = now.getHours().toString().padStart(2, '0');
    const minutes = now.getMinutes().toString().padStart(2, '0');
    const seconds = now.getSeconds().toString().padStart(2, '0');

    return `${year}-${month}-${day}`;
}
//带周的格式化
export function formatDateWithWeekday(dateStr,week) {
    const date = new Date(dateStr);
    const day = date.getDate();
    const month = date.getMonth() + 1; // 月份是从0开始的
    let showMonth = month;
    if (month>=1 && month<=9) {
        showMonth = "0"+month;
    }
    const year = date.getFullYear();
    const hours = date.getHours();
    const minutes = date.getMinutes();
    let showMinutes = minutes;
    if (minutes>=0 && minutes<9) {
        showMinutes = "0"+minutes;
    }

    return `${year}.${showMonth}.${day}  ${week} ${hours}:${showMinutes}`;
}



export function isPhoneNumber(value) {
    // 正则表达式匹配手机号
    return /^1(3|4|5|6|7|8|9)[0-9]\d{8}$/.test(value);
}




export function isEmailAddress(value) {
    // 正则表达式匹配邮箱
    return /^\w+([-+.]\w+)*@\w+([-.]\w+)*\.\w+([-.]\w+)*$/.test(value);
}

const emitter = mitt();
export const useMitt = () => emitter;
