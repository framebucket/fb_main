import { createContext, useContext } from 'react';

export const UserContext = createContext(null);

/** { user, setUser, reloadUser } for the signed-in photographer. */
export function useUser() {
  return useContext(UserContext);
}
