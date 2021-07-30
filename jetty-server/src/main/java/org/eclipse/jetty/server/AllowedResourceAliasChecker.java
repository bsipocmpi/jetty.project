//
//  ========================================================================
//  Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;

/**
 * <p>This will approve any alias to anything inside of the {@link ContextHandler}s resource base which
 * is not protected by {@link ContextHandler#isProtectedTarget(String)}.</p>
 * <p>This will approve symlinks to outside of the resource base. This can be optionally configured to check that the
 * target of the symlinks is also inside of the resource base and is not a protected target.</p>
 * <p>Aliases approved by this may still be able to bypass SecurityConstraints, so this class would need to be extended
 * to enforce any additional security constraints that are required.</p>
 */
public class AllowedResourceAliasChecker extends AbstractLifeCycle implements ContextHandler.AliasCheck
{
    private static final Logger LOG = Log.getLogger(AllowedResourceAliasChecker.class);
    private static final LinkOption[] FOLLOW_LINKS = new LinkOption[0];
    private static final LinkOption[] NO_FOLLOW_LINKS = new LinkOption[]{LinkOption.NOFOLLOW_LINKS};

    private final ContextHandler _contextHandler;
    private final boolean _checkSymlinkTargets;
    private final HashSet<Path> _protectedPaths = new HashSet<>();
    private Path _basePath;

    /**
     * @param contextHandler the context handler to use.
     * @param checkSymlinkTargets true to check that the target of the symlink is an allowed resource.
     */
    public AllowedResourceAliasChecker(ContextHandler contextHandler, boolean checkSymlinkTargets)
    {
        _contextHandler = contextHandler;
        _checkSymlinkTargets = checkSymlinkTargets;
    }

    protected ContextHandler getContextHandler()
    {
        return _contextHandler;
    }

    protected Path getBasePath()
    {
        return _basePath;
    }

    @Override
    protected void doStart() throws Exception
    {
        _basePath = getPath(_contextHandler.getBaseResource());
        String[] protectedTargets = _contextHandler.getProtectedTargets();
        if (protectedTargets != null)
        {
            for (String s : protectedTargets)
            {
                _protectedPaths.add(new File(_basePath.toFile(), s).toPath());
            }
        }
    }

    @Override
    protected void doStop() throws Exception
    {
        _basePath = null;
        _protectedPaths.clear();
    }

    @Override
    public boolean check(String uri, Resource resource)
    {
        if (_basePath == null)
            return false;

        // The existence check resolves the symlinks.
        if (!resource.exists())
            return false;

        Path resourcePath = getPath(resource);
        if (resourcePath == null)
            return false;

        try
        {
            if (isProtectedPath(resourcePath, NO_FOLLOW_LINKS))
                return false;

            if (_checkSymlinkTargets && hasSymbolicLink(resourcePath))
            {
                if (isProtectedPath(resourcePath, FOLLOW_LINKS))
                    return false;
            }
        }
        catch (Throwable t)
        {
            LOG.warn(t);
            return false;
        }

        return true;
    }

    /**
     * <p>Determines whether the provided resource path is protected.</p>
     *
     * <p>The resource path is protected if it is under one of the protected targets defined by
     * {@link ContextHandler#isProtectedTarget(String)} in which case the alias should not be allowed.
     * The resource path may also attempt to traverse above the root path and should be denied.</p>
     *
     * @param resourcePath the resource {@link Path} to be tested.
     * @param linkOptions an array of {@link LinkOption} to be provided to the {@link Path#toRealPath(LinkOption...)} method.
     * @return true if the resource path is protected and the alias should not be allowed.
     * @throws IOException if an I/O error occurs.
     */
    protected boolean isProtectedPath(Path resourcePath, LinkOption[] linkOptions) throws IOException
    {
        // If the resource doesn't exist we cannot determine whether it is protected so we assume it is.
        if (!Files.exists(resourcePath, linkOptions))
            return true;

        Path basePath = _basePath.toRealPath(linkOptions);
        Path targetPath = resourcePath.toRealPath(linkOptions);
        String target = targetPath.toString();

        // The target path must be under the base resource directory.
        if (!targetPath.startsWith(basePath))
            return true;

        for (Path protectedPath : _protectedPaths)
        {
            String protect;
            if (Files.exists(protectedPath, linkOptions))
                protect = protectedPath.toRealPath(linkOptions).toString();
            else if (linkOptions == NO_FOLLOW_LINKS)
                protect = protectedPath.normalize().toAbsolutePath().toString();
            else
                protect = protectedPath.toFile().getCanonicalPath();

            // If the target path is protected then we will not allow it.
            if (StringUtil.startsWithIgnoreCase(target, protect))
            {
                if (target.length() == protect.length())
                    return true;

                // Check that the target prefix really is a path segment.
                if (target.charAt(protect.length()) == File.separatorChar)
                    return true;
            }
        }

        return false;
    }

    protected boolean hasSymbolicLink(Path path)
    {
        return hasSymbolicLink(path.getRoot(), path);
    }

    protected boolean hasSymbolicLink(Path base, Path path)
    {
        for (Path p = path; (p != null) && !p.equals(base); p = p.getParent())
        {
            if (Files.isSymbolicLink(p))
                return true;
        }

        return false;
    }

    private Path getPath(Resource resource)
    {
        try
        {
            if (resource instanceof PathResource)
                return ((PathResource)resource).getPath();
            return resource.getFile().toPath();
        }
        catch (Throwable t)
        {
            LOG.ignore(t);
            return null;
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{checkSymlinkTargets=%s}", AllowedResourceAliasChecker.class.getSimpleName(), hashCode(), _checkSymlinkTargets);
    }
}