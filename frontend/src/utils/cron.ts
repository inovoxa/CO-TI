/**
 * CRON 表达式工具类
 * 提供 CRON 表达式的解析、格式化、验证等功能
 */

/** CRON 字段类型枚举 */
export enum CronFieldType {
  SECOND = 'second',
  MINUTE = 'minute',
  HOUR = 'hour',
  DAY = 'day',
  MONTH = 'month',
  WEEK = 'week',
  YEAR = 'year'
}

/** CRON 字段配置 */
export interface CronFieldConfig {
  key: CronFieldType
  label: string
  min: number
  max: number
  names?: Record<string, number> // 名称映射，如月份名称
}

/** CRON 字段配置常量 */
export const CRON_FIELD_CONFIGS: Record<CronFieldType, CronFieldConfig> = {
  [CronFieldType.SECOND]: { key: CronFieldType.SECOND, label: 'Segundo', min: 0, max: 59 },
  [CronFieldType.MINUTE]: { key: CronFieldType.MINUTE, label: 'Minuto', min: 0, max: 59 },
  [CronFieldType.HOUR]: { key: CronFieldType.HOUR, label: 'Hora', min: 0, max: 23 },
  [CronFieldType.DAY]: { key: CronFieldType.DAY, label: 'Dia', min: 1, max: 31 },
  [CronFieldType.MONTH]: {
    key: CronFieldType.MONTH,
    label: 'Mês',
    min: 1,
    max: 12,
    names: {
      JAN: 1,
      FEB: 2,
      MAR: 3,
      APR: 4,
      MAY: 5,
      JUN: 6,
      JUL: 7,
      AUG: 8,
      SEP: 9,
      OCT: 10,
      NOV: 11,
      DEC: 12
    }
  },
  [CronFieldType.WEEK]: {
    key: CronFieldType.WEEK,
    label: 'Semana',
    min: 0,
    max: 7,
    names: {
      SUN: 0,
      MON: 1,
      TUE: 2,
      WED: 3,
      THU: 4,
      FRI: 5,
      SAT: 6
    }
  },
  [CronFieldType.YEAR]: { key: CronFieldType.YEAR, label: 'Ano', min: 1970, max: 2099 }
}

/** 解析后的 CRON 字段 */
export interface ParsedCronField {
  type: 'any' | 'specific' | 'range' | 'step' | 'list' | 'last' | 'weekday' | 'nth'
  values: number[]
  original: string
  description: string
}

/** 解析后的 CRON 表达式 */
export interface ParsedCronExpression {
  second: ParsedCronField
  minute: ParsedCronField
  hour: ParsedCronField
  day: ParsedCronField
  month: ParsedCronField
  week: ParsedCronField
  year?: ParsedCronField
  isValid: boolean
  description: string
  nextExecutionTime?: Date
}

/** 常用 CRON 表达式预设 */
export const CRON_PRESETS = {
  EVERY_SECOND: '* * * * * ?',
  EVERY_MINUTE: '0 * * * * ?',
  EVERY_HOUR: '0 0 * * * ?',
  EVERY_DAY: '0 0 0 * * ?',
  EVERY_WEEK: '0 0 0 ? * 1',
  EVERY_MONTH: '0 0 0 1 * ?',
  EVERY_YEAR: '0 0 0 1 1 ?',
  WORKDAY_9AM: '0 0 9 ? * 2-6', // 工作日上午9点
  WORKDAY_6PM: '0 0 18 ? * 2-6', // 工作日下午6点
  WEEKEND_10AM: '0 0 10 ? * 1,7' // 周末上午10点
} as const

/** CRON 表达式工具类 */
export class CronUtils {
  /** 验证 CRON 表达式格式 */
  static validate(cronExpression: string): boolean {
    if (!cronExpression || typeof cronExpression !== 'string') {
      return false
    }

    const parts = cronExpression.trim().split(/\s+/)

    // 支持 5-7 个字段的 CRON 表达式
    if (parts.length < 5 || parts.length > 7) {
      return false
    }

    // 基本格式验证
    const cronRegex = /^[0-9*\/\-,?LW#]+$/
    return parts.every((part) => cronRegex.test(part))
  }

  /** 解析单个 CRON 字段 */
  static parseField(
    fieldValue: string,
    fieldType: CronFieldType,
    config: CronFieldConfig
  ): ParsedCronField {
    const field: ParsedCronField = {
      type: 'any',
      values: [],
      original: fieldValue,
      description: ''
    }

    // 处理特殊字符
    if (fieldValue === '*' || fieldValue === '?') {
      field.type = 'any'
      field.description = `A cada ${config.label}`
      return field
    }

    // 处理最后一天 (L)
    if (fieldValue === 'L' && fieldType === CronFieldType.DAY) {
      field.type = 'last'
      field.description = 'Último dia do mês'
      return field
    }

    // 处理范围 (-)
    if (fieldValue.includes('-')) {
      const [start, end] = fieldValue.split('-').map(Number)
      if (!isNaN(start) && !isNaN(end) && start >= config.min && end <= config.max) {
        field.type = 'range'
        field.values = Array.from({ length: end - start + 1 }, (_, i) => start + i)
        field.description = `${config.label} ${start}-${end}`
      }
      return field
    }

    // 处理步长 (/)
    if (fieldValue.includes('/')) {
      const [base, step] = fieldValue.split('/')
      const stepNum = Number(step)
      if (!isNaN(stepNum) && stepNum > 0) {
        field.type = 'step'
        if (base === '*') {
          field.description = `A cada ${stepNum} ${config.label}`
        } else {
          const startNum = Number(base)
          field.description = `A partir de ${startNum}, a cada ${stepNum} ${config.label}`
        }
      }
      return field
    }

    // 处理列表 (,)
    if (fieldValue.includes(',')) {
      const values = fieldValue
        .split(',')
        .map(Number)
        .filter((n) => !isNaN(n))
      if (values.length > 0) {
        field.type = 'list'
        field.values = values
        field.description = `${config.label} ${values.join(',')}`
      }
      return field
    }

    // 处理具体数值
    const numValue = Number(fieldValue)
    if (!isNaN(numValue) && numValue >= config.min && numValue <= config.max) {
      field.type = 'specific'
      field.values = [numValue]
      field.description = `${config.label} ${numValue}`
    }

    return field
  }

  /** 解析完整的 CRON 表达式 */
  static parse(cronExpression: string): ParsedCronExpression {
    const result: ParsedCronExpression = {
      second: { type: 'any', values: [], original: '*', description: 'A cada segundo' },
      minute: { type: 'any', values: [], original: '*', description: 'A cada minuto' },
      hour: { type: 'any', values: [], original: '*', description: 'A cada hora' },
      day: { type: 'any', values: [], original: '*', description: 'Todo dia' },
      month: { type: 'any', values: [], original: '*', description: 'Todo mês' },
      week: { type: 'any', values: [], original: '?', description: 'Qualquer dia da semana' },
      isValid: false,
      description: ''
    }

    if (!this.validate(cronExpression)) {
      result.description = 'Expressão CRON inválida'
      return result
    }

    const parts = cronExpression.trim().split(/\s+/)
    const fieldTypes = [
      CronFieldType.SECOND,
      CronFieldType.MINUTE,
      CronFieldType.HOUR,
      CronFieldType.DAY,
      CronFieldType.MONTH,
      CronFieldType.WEEK
    ]

    // 如果只有5个字段，则第一个字段是分钟
    const startIndex = parts.length === 5 ? 1 : 0

    for (let i = 0; i < parts.length; i++) {
      const fieldType = fieldTypes[i + startIndex]
      if (fieldType && CRON_FIELD_CONFIGS[fieldType]) {
        const config = CRON_FIELD_CONFIGS[fieldType]
        result[fieldType] = this.parseField(parts[i], fieldType, config)
      }
    }

    // 处理年份字段（如果存在）
    if (parts.length === 7) {
      const yearConfig = CRON_FIELD_CONFIGS[CronFieldType.YEAR]
      result.year = this.parseField(parts[6], CronFieldType.YEAR, yearConfig)
    }

    result.isValid = true
    result.description = this.generateDescription(result)

    return result
  }

  /** 生成 CRON 表达式的可读描述 */
  static generateDescription(parsed: ParsedCronExpression): string {
    const parts: string[] = []

    // 构建时间部分描述
    if (parsed.hour.type === 'specific' && parsed.minute.type === 'specific') {
      const hour = parsed.hour.values[0]
      const minute = parsed.minute.values[0]
      parts.push(`${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}`)
    } else if (parsed.hour.type === 'specific') {
      parts.push(`Todo dia às ${parsed.hour.values[0]}h`)
    } else if (parsed.minute.type === 'specific' && parsed.minute.values[0] === 0) {
      if (parsed.hour.type === 'any') {
        parts.push('A cada hora cheia')
      }
    } else if (parsed.minute.type === 'step') {
      const step = parsed.minute.original.split('/')[1]
      parts.push(`A cada ${step} minutos`)
    } else if (parsed.hour.type === 'step') {
      const step = parsed.hour.original.split('/')[1]
      parts.push(`A cada ${step} horas`)
    }

    // 构建日期部分描述
    if (parsed.day.type === 'specific') {
      parts.push(`Todo dia ${parsed.day.values[0]} do mês`)
    } else if (parsed.week.type === 'specific') {
      const weekNames = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado']
      const weekDay = parsed.week.values[0]
      if (weekDay >= 0 && weekDay <= 6) {
        parts.push(`Toda ${weekNames[weekDay]}`)
      }
    } else if (parsed.week.type === 'range') {
      parts.push('Dias úteis')
    }

    // 构建月份部分描述
    if (parsed.month.type === 'specific') {
      parts.push(`Mês ${parsed.month.values[0]}`)
    }

    return parts.length > 0 ? parts.join(' ') : 'Regra de horário personalizada'
  }

  /** 格式化 CRON 表达式为可读文本 */
  static format(cronExpression: string): string {
    if (!cronExpression) return ''

    const parsed = this.parse(cronExpression)
    return parsed.isValid ? parsed.description : cronExpression
  }

  /** 获取预设的 CRON 表达式列表 */
  static getPresets() {
    return Object.entries(CRON_PRESETS).map(([key, value]) => ({
      label: this.format(value),
      value,
      key
    }))
  }

  /** 计算 CRON 表达式的下次执行时间 */
  static getNextExecutionTime(cronExpression: string, fromDate?: Date): Date | null {
    const parsed = this.parse(cronExpression)
    if (!parsed.isValid) {
      return null
    }

    const now = fromDate || new Date()
    // eslint-disable-next-line prefer-const
    let nextTime = new Date(now.getTime() + 1000) // 从下一秒开始

    // 简化版本：处理常见的 CRON 表达式模式
    // 对于复杂的 CRON 表达式，建议使用专门的库如 node-cron 或 cron-parser

    // 处理每分钟执行
    if (parsed.second.type === 'specific' && parsed.minute.type === 'any') {
      const targetSecond = parsed.second.values[0]
      nextTime.setSeconds(targetSecond, 0)
      if (nextTime <= now) {
        nextTime.setMinutes(nextTime.getMinutes() + 1)
      }
      return nextTime
    }

    // 处理每小时执行
    if (
      parsed.second.type === 'specific' &&
      parsed.minute.type === 'specific' &&
      parsed.hour.type === 'any'
    ) {
      const targetSecond = parsed.second.values[0]
      const targetMinute = parsed.minute.values[0]
      nextTime.setMinutes(targetMinute, targetSecond, 0)
      if (nextTime <= now) {
        nextTime.setHours(nextTime.getHours() + 1)
      }
      return nextTime
    }

    // 处理每天执行
    if (
      parsed.second.type === 'specific' &&
      parsed.minute.type === 'specific' &&
      parsed.hour.type === 'specific'
    ) {
      const targetSecond = parsed.second.values[0]
      const targetMinute = parsed.minute.values[0]
      const targetHour = parsed.hour.values[0]

      nextTime.setHours(targetHour, targetMinute, targetSecond, 0)
      if (nextTime <= now) {
        nextTime.setDate(nextTime.getDate() + 1)
      }
      return nextTime
    }

    // 处理步长执行
    if (parsed.minute.type === 'step') {
      const step = parseInt(parsed.minute.original.split('/')[1])
      const currentMinute = nextTime.getMinutes()
      const nextMinute = Math.ceil(currentMinute / step) * step

      if (nextMinute >= 60) {
        nextTime.setHours(nextTime.getHours() + 1, 0, 0, 0)
      } else {
        nextTime.setMinutes(nextMinute, 0, 0)
      }
      return nextTime
    }

    // 对于其他复杂情况，返回一个估算时间
    return new Date(now.getTime() + 60000) // 1分钟后
  }

  /** 获取 CRON 表达式的执行频率描述 */
  static getFrequencyDescription(cronExpression: string): string {
    const parsed = this.parse(cronExpression)
    if (!parsed.isValid) {
      return 'Expressão inválida'
    }

    // 计算大概的执行频率
    if (parsed.second.type === 'any' && parsed.minute.type === 'any') {
      return 'Executa a cada segundo'
    }

    if (parsed.minute.type === 'any' && parsed.hour.type === 'any') {
      return 'Executa a cada minuto'
    }

    if (parsed.hour.type === 'any' && parsed.day.type === 'any') {
      return 'Executa a cada hora'
    }

    if (parsed.day.type === 'any' && parsed.month.type === 'any') {
      return 'Executa todo dia'
    }

    if (parsed.month.type === 'any') {
      return 'Executa todo mês'
    }

    return 'Executa conforme agenda'
  }

  /** 检查 CRON 表达式是否会在指定时间执行 */
  static willExecuteAt(cronExpression: string, targetDate: Date): boolean {
    const parsed = this.parse(cronExpression)
    if (!parsed.isValid) {
      return false
    }

    // 检查各个字段是否匹配
    const second = targetDate.getSeconds()
    const minute = targetDate.getMinutes()
    const hour = targetDate.getHours()
    const day = targetDate.getDate()
    const month = targetDate.getMonth() + 1
    const weekDay = targetDate.getDay()

    return (
      this.fieldMatches(parsed.second, second) &&
      this.fieldMatches(parsed.minute, minute) &&
      this.fieldMatches(parsed.hour, hour) &&
      this.fieldMatches(parsed.day, day) &&
      this.fieldMatches(parsed.month, month) &&
      (parsed.week.type === 'any' || this.fieldMatches(parsed.week, weekDay))
    )
  }

  /** 检查字段值是否匹配 */
  private static fieldMatches(field: ParsedCronField, value: number): boolean {
    if (field.type === 'any') {
      return true
    }

    if (field.type === 'specific' || field.type === 'list') {
      return field.values.includes(value)
    }

    if (field.type === 'range') {
      return value >= field.values[0] && value <= field.values[field.values.length - 1]
    }

    if (field.type === 'step') {
      const [base, step] = field.original.split('/').map(Number)
      if (base === 0 || field.original.startsWith('*')) {
        return value % step === 0
      }
      return value >= base && (value - base) % step === 0
    }

    return false
  }
}
