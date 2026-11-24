import request from '@/config/axios'
import type { ImportacaoPrevia, ImportacaoResultado } from '@/api/monitoramento'

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

// ========== Sistema → Usuários (lista, ficha, lote, revisão, planilha) ==========

export interface PerfilUsuario {
  id: number
  nome: string
  codigo: string
  escopo?: string
}
export interface UsuarioLinha {
  id: number
  username: string
  nickname: string
  email?: string
  mobile?: string
  deptId?: number
  deptNome?: string
  /** Unidade pela máquina que a pessoa usa */
  unidadeId?: number
  unidadeNome?: string
  unidadeMaquina?: string
  unidadeIp?: string
  unidadeOrigem?: 'manual' | 'faixa' | 'fora' | 'sem_maquina'
  status: number
  perfis: PerfilUsuario[]
  privilegiado: boolean
  origem: 'ad' | 'local'
  adAtivo?: boolean
  adSincronizacao?: string
  mfa: boolean
  ultimoAcesso?: string
  ultimoIp?: string
  criadoEm?: string
  sessoes: number
}
export interface UsuarioFicha {
  usuario: UsuarioLinha
  observacao?: string
  adLogin?: string
  adDn?: string
  adGestorDn?: string
  mfaAtivadoEm?: string
  sessoes: { criada: string; expira: string; cliente?: string }[]
  acessos: { quando: string; ip?: string; navegador?: string; resultado: number; tipo: string }[]
  auditoria: { momento: string; acao: string; ip?: string; detalhes?: string }[]
}
export interface LoteUsuariosResp {
  feitos: number
  ignorados: number
  avisos: string[]
}
export interface RevisaoAcessos {
  privilegiados: UsuarioLinha[]
  privilegiadosSemMfa: UsuarioLinha[]
  semAcesso: UsuarioLinha[]
  locais: UsuarioLinha[]
  desativadosNoAd: UsuarioLinha[]
  diasSemAcesso: number
  ultimaRevisao?: string
  revisadoPor?: string
}
export const getUsuarios = (params: any) =>
  request.get({ url: '/identidade/usuarios/page', params })
export const getFichaUsuario = (id: number): Promise<UsuarioFicha> =>
  request.get({ url: '/identidade/usuarios/ficha', params: { id } })
export const loteUsuarios = (data: {
  acao: string
  ids: number[]
  deptId?: number
  roleId?: number
}): Promise<LoteUsuariosResp> => request.post({ url: '/identidade/usuarios/lote', data })
export const getRevisaoAcessos = (): Promise<RevisaoAcessos> =>
  request.get({ url: '/identidade/usuarios/revisao' })
export const marcarRevisado = (observacao?: string) =>
  request.post({ url: '/identidade/usuarios/revisado', data: { observacao } })
export const exportarUsuarios = (params: any): Promise<Blob> =>
  request.download({ url: '/identidade/usuarios/exportar', params })
const enviarPlanilha = async <T>(url: string, data: object): Promise<T> =>
  (await request.upload<{ data: T }>({ url, data })).data
export const analisarImportacaoUsuarios = (arquivo: File) =>
  enviarPlanilha<ImportacaoPrevia>('/identidade/usuarios/importar/analisar', { arquivo })
export const importarUsuarios = (arquivo: File, removerIds: number[]) =>
  enviarPlanilha<ImportacaoResultado>('/identidade/usuarios/importar', {
    arquivo,
    removerIds: removerIds.join(',')
  })
