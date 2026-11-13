import { createContext, useContext } from 'react';

/** 'phone' for the Android/iOS interface (/app), 'desktop' for the Windows/Mac app (/desktop). */
export const LayoutContext = createContext('phone');

export function useLayout() {
  return useContext(LayoutContext);
}

/** Class for screens that reuse the website's panels: squeezed to phone width, or left as they are. */
export function useEmbedClass() {
  return useContext(LayoutContext) === 'desktop' ? 'd-embed' : 'm-embed';
}
