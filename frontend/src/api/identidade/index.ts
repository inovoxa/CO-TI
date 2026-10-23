import request from '@/config/axios'

// ========== Verificação em duas etapas (do próprio usuário) ==========

export interface MfaStatus {
  ativo: boolean
  codigosRecuperacaoRestantes: number
}

export interface MfaCadastro {
  segredo: string
  uri: string
}

export const getMfaStatus = (): Promise<MfaStatus> => {
  return request.get({ url: '/identidade/mfa/status' })
}

export const iniciarMfa = (): Promise<MfaCadastro> => {
  return request.post({ url: '/identidade/mfa/iniciar' })
}

export const confirmarMfa = (codigo: string): Promise<string[]> => {
  return request.post({ url: '/identidade/mfa/confirmar', data: { codigo } })
}

export const desativarMfa = (codigo: string) => {
  return request.post({ url: '/identidade/mfa/desativar', data: { codigo } })
}

/** Administrador: remove o MFA de quem perdeu o celular e os códigos */
export const redefinirMfaUsuario = (id: number) => {
  return request.delete({ url: '/identidade/mfa/usuario?id=' + id })
}

// ========== Trilha de auditoria ==========

export interface AuditoriaVO {
  id: number
  momento: string
  acao: string
  userId?: number
  usuario?: string
  ip?: string
  detalhes: string
  hashAnterior: string
  hash: string
}

export interface VerificacaoAuditoria {
  integra: boolean
  registrosConferidos: number
  primeiroIdQuebrado?: number
  motivo?: string
}

export const getAuditoriaPage = (params: any) => {
  return request.get({ url: '/identidade/auditoria/page', params })
}

export const verificarAuditoria = (): Promise<VerificacaoAuditoria> => {
  return request.get({ url: '/identidade/auditoria/verificar' })
}
